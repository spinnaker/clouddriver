/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.cats.redis.cache;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.hash.Hashing;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.cache.CacheFilter;
import com.netflix.spinnaker.cats.cache.DefaultCacheData;
import com.netflix.spinnaker.cats.cache.WriteableCache;
import com.netflix.spinnaker.cats.redis.JedisSource;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.Pipeline;
import redis.clients.jedis.Protocol;
import redis.clients.jedis.ScanParams;
import redis.clients.jedis.ScanResult;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Serializable;
import java.nio.charset.Charset;
import java.util.*;
import java.util.function.Predicate;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class RedisCache implements WriteableCache {

    public interface CacheMetrics {
        default void merge(String prefix,
                           String type,
                           int itemCount,
                           int keysWritten,
                           int relationshipCount,
                           int hashMatches,
                           int hashUpdates,
                           int saddOperations,
                           int msetOperations,
                           int hmsetOperations,
                           int pipelineOperations,
                           int expireOperations) {
            //noop
        }

        default void evict(String prefix,
                           String type,
                           int itemCount,
                           int keysDeleted,
                           int hashesDeleted,
                           int delOperations,
                           int hdelOperations,
                           int sremOperations) {
            //noop
        }

        default void get(String prefix,
                         String type,
                         int itemCount,
                         int requestedSize,
                         int keysRequested,
                         int relationshipsRequested,
                         int mgetOperationCount) {
            //noop
        }

        class NOOP implements CacheMetrics {
        }
    }

    private static final TypeReference<Map<String, Object>> ATTRIBUTES = new TypeReference<Map<String, Object>>() {
    };
    private static final TypeReference<Collection<String>> RELATIONSHIPS = new TypeReference<Collection<String>>() {
    };

    private final String prefix;
    private final JedisSource source;
    private final CacheMetrics cacheMetrics;
    private final RedisCacheOptions options;
    private final ValueCodec<Map<String, Object>> attributesCodec;
    private final ValueCodec<Collection<String>> relationshipsCodec;

    public RedisCache(String prefix, JedisSource source, ObjectMapper objectMapper, RedisCacheOptions options, CacheMetrics cacheMetrics) {
        this.prefix = prefix;
        this.source = source;
        ObjectMapper om = objectMapper.disable(SerializationFeature.WRITE_NULL_MAP_VALUES);
        this.options = options;
        this.cacheMetrics = cacheMetrics == null ? new CacheMetrics.NOOP() : cacheMetrics;
        attributesCodec = new ValueCodec<>(om, options.isCompressionEnabled(), ATTRIBUTES, Map::isEmpty);
        relationshipsCodec = new ValueCodec<>(om, options.isCompressionEnabled(), RELATIONSHIPS, Collection::isEmpty);
    }

    @Override
    public void merge(String type, CacheData item) {
        mergeAll(type, Collections.singletonList(item));
    }

    @Override
    public void mergeAll(String type, Collection<CacheData> items) {
        for (List<CacheData> partition : Iterables.partition(items, options.getMaxMergeBatchSize())) {
            mergeItems(type, partition);
        }
    }

    private void mergeItems(String type, Collection<CacheData> items) {
        if (items.isEmpty()) {
            return;
        }
        final Set<String> relationshipNames = new HashSet<>();
        final List<byte[]> keysToSet = new LinkedList<>();
        final Set<String> idSet = new HashSet<>();

        final Map<byte[], Integer> ttlSecondsByKey = new HashMap<>();
        int skippedWrites = 0;

        final Map<String, byte[]> hashes = getHashes(type, items);

        final NavigableMap<byte[], byte[]> updatedHashes = new TreeMap<>(new ByteArrayComparator());

        for (CacheData item : items) {
            MergeOp op = buildMergeOp(type, item, hashes);
            relationshipNames.addAll(op.relNames);
            keysToSet.addAll(op.keysToSet);
            idSet.add(item.getId());
            updatedHashes.putAll(op.hashesToSet);
            skippedWrites += op.skippedWrites;

            if (item.getTtlSeconds() > 0) {
                for (byte[] key : op.keysToSet) {
                    ttlSecondsByKey.put(key, item.getTtlSeconds());
                }
            }
        }

        int saddOperations = 0;
        int msetOperations = 0;
        int hmsetOperations = 0;
        int pipelineOperations = 0;
        int expireOperations = 0;
        if (keysToSet.size() > 0) {
            try (Jedis jedis = source.getJedis()) {
                Pipeline pipeline = jedis.pipelined();
                for (List<String> idPart : Iterables.partition(idSet, options.getMaxSaddSize())) {
                    final String[] ids = idPart.toArray(new String[idPart.size()]);
                    pipeline.sadd(allOfTypeReindex(type), ids);
                    saddOperations++;
                    pipeline.sadd(allOfTypeId(type), ids);
                    saddOperations++;
                }

                for (List<byte[]> keys : Lists.partition(keysToSet, options.getMaxMsetSize())) {
                    pipeline.mset(keys.toArray(new byte[keys.size()][]));
                    msetOperations++;
                }

                if (!relationshipNames.isEmpty()) {
                    for (List<String> relNamesPart : Iterables.partition(relationshipNames, options.getMaxSaddSize())) {
                        pipeline.sadd(allRelationshipsId(type), relNamesPart.toArray(new String[relNamesPart.size()]));
                        saddOperations++;
                    }
                }

                if (!updatedHashes.isEmpty()) {
                    for (List<byte[]> hashPart : Iterables.partition(updatedHashes.keySet(), options.getMaxHmsetSize())) {
                        pipeline.hmset(hashesId(type), updatedHashes.subMap(hashPart.get(0), true, hashPart.get(hashPart.size() - 1), true));
                        hmsetOperations++;
                    }
                }
                pipeline.sync();
                pipelineOperations++;
            }
            try (Jedis jedis = source.getJedis()) {
                for (List<Map.Entry<byte[], Integer>> ttlPart : Iterables.partition(ttlSecondsByKey.entrySet(), options.getMaxPipelineSize())) {
                    Pipeline pipeline = jedis.pipelined();
                    for (Map.Entry<byte[], Integer> ttlEntry : ttlPart) {
                        pipeline.expire(ttlEntry.getKey(), ttlEntry.getValue());
                    }
                    expireOperations += ttlPart.size();
                    pipeline.sync();
                    pipelineOperations++;
                }
            }
        }
        cacheMetrics.merge(
            prefix,
            type,
            items.size(),
            keysToSet.size() / 2,
            relationshipNames.size(),
            skippedWrites,
            updatedHashes.size(),
            saddOperations,
            msetOperations,
            hmsetOperations,
            pipelineOperations,
            expireOperations);
    }

    @Override
    public void evict(String type, String id) {
        evictAll(type, Collections.singletonList(id));
    }

    @Override
    public void evictAll(String type, Collection<String> identifiers) {
        if (identifiers.isEmpty()) {
            return;
        }
        final Collection<String> allRelationships = scanMembers(allRelationshipsId(type));
        for (List<String> items : Iterables.partition(new HashSet<>(identifiers), options.getMaxEvictBatchSize())) {
            evictItems(type, items, allRelationships);
        }
    }

    private void evictItems(String type, List<String> identifiers, Collection<String> allRelationships) {
        byte[][] delKeys = new byte[(allRelationships.size() + 1) * identifiers.size()][];
        int idx = 0;
        for (String id : identifiers) {
            for (String relationship : allRelationships) {
                delKeys[idx++] = relationshipId(type, id, relationship);
            }
            delKeys[idx++] = attributesId(type, id);
        }

        int delOperations = 0;
        int hdelOperations = 0;
        int sremOperations = 0;
        final byte[] hashesId = hashesId(type);
        try (Jedis jedis = source.getJedis()) {
            Pipeline pipe = jedis.pipelined();
            for (int i = 0; i < delKeys.length; i += options.getMaxDelSize()) {
                final int partSize = partitionSize(delKeys, i, options.getMaxDelSize());
                final byte[][] partition = Arrays.copyOfRange(delKeys, i, partSize);
                pipe.del(partition);
                delOperations++;
                pipe.hdel(hashesId, partition);
            }

            for (List<String> idPartition : Lists.partition(identifiers, options.getMaxDelSize())) {
                String[] ids = idPartition.toArray(new String[idPartition.size()]);
                pipe.srem(allOfTypeId(type), ids);
                sremOperations++;
                pipe.srem(allOfTypeReindex(type), ids);
                sremOperations++;
            }

            pipe.sync();
        }

        cacheMetrics.evict(
            prefix,
            type,
            identifiers.size(),
            delKeys.length,
            delKeys.length,
            delOperations,
            hdelOperations,
            sremOperations);
    }

    @Override
    public CacheData get(String type, String id) {
        return get(type, id, null);
    }

    @Override
    public CacheData get(String type, String id, CacheFilter cacheFilter) {
        Collection<CacheData> result = getAll(type, Collections.singletonList(id), cacheFilter);
        if (result.isEmpty()) {
            return null;
        }
        return result.iterator().next();
    }

    @Override
    public Collection<CacheData> getAll(String type, Collection<String> identifiers) {
        return getAll(type, identifiers, null);
    }

    @Override
    public Collection<CacheData> getAll(String type) {
        return getAll(type, (CacheFilter) null);
    }

    @Override
    public Collection<CacheData> getAll(String type, CacheFilter cacheFilter) {
        final Set<String> allIds = scanMembers(allOfTypeId(type));
        return getAll(type, allIds, cacheFilter);
    }

    @Override
    public Collection<CacheData> getAll(String type, String... identifiers) {
        return getAll(type, Arrays.asList(identifiers));
    }

    @Override
    public Collection<CacheData> getAll(String type,
                                        Collection<String> identifiers,
                                        CacheFilter cacheFilter) {
        if (identifiers.isEmpty()) {
            return Collections.emptySet();
        }
        Collection<String> ids = new LinkedHashSet<>(identifiers);
        final List<String> knownRels;
        Set<String> allRelationships = scanMembers(allRelationshipsId(type));
        if (cacheFilter == null) {
            knownRels = new ArrayList<>(allRelationships);
        } else {
            knownRels = new ArrayList<>(cacheFilter.filter(CacheFilter.Type.RELATIONSHIP, allRelationships));
        }

        Collection<CacheData> result = new ArrayList<>(ids.size());

        for (List<String> idPart : Iterables.partition(ids, options.getMaxGetBatchSize())) {
            result.addAll(getItems(type, idPart, knownRels));
        }

        return result;
    }

    private Collection<CacheData> getItems(String type, List<String> ids, List<String> knownRels) {

        final int singleResultSize = knownRels.size() + 1;

        final byte[][] keysToGet = new byte[singleResultSize * ids.size()][];
        int idx = 0;
        for (String id : ids) {
            keysToGet[idx++] = attributesId(type, id);
            for (String rel : knownRels) {
                keysToGet[idx++] = relationshipId(type, id, rel);
            }
        }

        final List<byte[]> keyResult = new ArrayList<>(keysToGet.length);

        int mgetOperations = 0;
        try (Jedis jedis = source.getJedis()) {
            for (int i = 0; i < keysToGet.length; i += options.getMaxMgetSize()) {
                int partSize = partitionSize(keysToGet, i, options.getMaxMgetSize());
                keyResult.addAll(jedis.mget(Arrays.copyOfRange(keysToGet, 0, partSize)));
                mgetOperations++;
            }
        }

        if (keyResult.size() != keysToGet.length) {
            throw new RuntimeException("Expected same size result as request");
        }

        Collection<CacheData> results = new ArrayList<>(ids.size());
        Iterator<String> idIterator = ids.iterator();
        for (int ofs = 0; ofs < keyResult.size(); ofs += singleResultSize) {
            CacheData item = extractItem(idIterator.next(), keyResult.subList(ofs, ofs + singleResultSize), knownRels);
            if (item != null) {
                results.add(item);
            }
        }

        cacheMetrics.get(prefix, type, results.size(), ids.size(), keysToGet.length, knownRels.size(), mgetOperations);
        return results;
    }

    @Override
    public Collection<String> getIdentifiers(String type) {
        return scanMembers(allOfTypeId(type));
    }

    @Override
    public Collection<String> filterIdentifiers(String type, String glob) {
        return scanMembers(allOfTypeId(type), Optional.of(glob));
    }

    private Set<String> scanMembers(String setKey) {
        return scanMembers(setKey, Optional.empty());
    }

    private Set<String> scanMembers(String setKey, Optional<String> glob) {
        try (Jedis jedis = source.getJedis()) {
            final Set<String> matches = new HashSet<>();
            final ScanParams scanParams = new ScanParams().count(options.getScanSize());
            glob.ifPresent(scanParams::match);
            String cursor = "0";
            while (true) {
                final ScanResult<String> scanResult = jedis.sscan(setKey, cursor, scanParams);
                matches.addAll(scanResult.getResult());
                cursor = scanResult.getStringCursor();
                if ("0".equals(cursor)) {
                    return matches;
                }
            }
        }
    }

    private String bytesToString(byte[] bytes) {
        return new String(bytes, Charset.forName(Protocol.CHARSET));
    }
    private byte[] stringToBytes(String string) {
        return string.getBytes(Charset.forName(Protocol.CHARSET));
    }

    private byte[][] stringsToBytes(Collection<String> strings) {
        final byte[][] results = new byte[strings.size()][];
        int i = 0;
        for (String string : strings) {
            results[i++] = stringToBytes(string);
        }
        return results;
    }

    private static class MergeOp {
        final Set<String> relNames;
        final List<byte[]> keysToSet;
        final Map<byte[], byte[]> hashesToSet;
        final int skippedWrites;

        public MergeOp(Set<String> relNames, List<byte[]> keysToSet, Map<byte[], byte[]> hashesToSet, int skippedWrites) {
            this.relNames = relNames;
            this.keysToSet = keysToSet;
            this.hashesToSet = hashesToSet;
            this.skippedWrites = skippedWrites;
        }
    }

    /**
     * Compares the hash of serializedValue against an existing hash, if they do not match adds
     * serializedValue to keys and the new hash to updatedHashes.
     *
     * @param hashes          the existing hash values
     * @param id              the id of the item
     * @param serializedValue the serialized value
     * @param keys            values to persist - if the hash does not match id and serializedValue are appended
     * @param updatedHashes   hashes to persist - if the hash does not match adds an entry of id -> computed hash
     * @return true if the hash matched, false otherwise
     */
    private boolean hashCheck(Map<String, byte[]> hashes, byte[] id, byte[] serializedValue, List<byte[]> keys, Map<byte[], byte[]> updatedHashes) {
        if (options.isHashingEnabled()) {
            final byte[] hash = Hashing.sha1().newHasher().putBytes(serializedValue).hash().asBytes();
            final byte[] existingHash = hashes.get(bytesToString(id));
            if (Arrays.equals(hash, existingHash)) {
                return true;
            }
            updatedHashes.put(id, hash);
        }

        keys.add(id);
        keys.add(serializedValue);
        return false;
    }

    private MergeOp buildMergeOp(String type, CacheData cacheData, Map<String, byte[]> hashes) {
        int skippedWrites = 0;
        final byte[] serializedAttributes = attributesCodec.encode(cacheData.getAttributes());
        final Map<byte[], byte[]> hashesToSet = new HashMap<>();
        final List<byte[]> keysToSet = new ArrayList<>((cacheData.getRelationships().size() + 1) * 2);
        if (serializedAttributes != null &&
            hashCheck(hashes, attributesId(type, cacheData.getId()), serializedAttributes, keysToSet, hashesToSet)) {
            skippedWrites++;
        }

        if (!cacheData.getRelationships().isEmpty()) {
            for (Map.Entry<String, Collection<String>> relationship : cacheData.getRelationships().entrySet()) {
                final byte[] relationshipValue = relationshipsCodec.encode(new LinkedHashSet<>(relationship.getValue()));
                if (hashCheck(hashes, relationshipId(type, cacheData.getId(), relationship.getKey()), relationshipValue, keysToSet, hashesToSet)) {
                    skippedWrites++;
                }
            }
        }

        return new MergeOp(cacheData.getRelationships().keySet(), keysToSet, hashesToSet, skippedWrites);
    }

    private boolean isHashingDisabled(String type) {
        if (!options.isHashingEnabled()) {
            return true;
        }
        try (Jedis jedis = source.getJedis()) {
            return jedis.exists(hashesDisabled(type));
        }
    }

    private List<String> getKeys(String type, Collection<CacheData> cacheDatas) {
        final Collection<String> keys = new HashSet<>();
        for (CacheData cacheData : cacheDatas) {
            if (!cacheData.getAttributes().isEmpty()) {
                keys.add(attributesIdString(type, cacheData.getId()));
            }
            if (!cacheData.getRelationships().isEmpty()) {
                for (String relationship : cacheData.getRelationships().keySet()) {
                    keys.add(relationshipIdString(type, cacheData.getId(), relationship));
                }
            }
        }
        return new ArrayList<>(keys);
    }

    private static int partitionSize(Object[] src, int index, int maxPartition) {
        return Math.min(index + maxPartition, src.length);
    }

    private Map<String, byte[]> getHashes(String type, Collection<CacheData> items) {
        if (isHashingDisabled(type)) {
            return Collections.emptyMap();
        }

        final List<String> hashKeys = getKeys(type, items);
        if (hashKeys.isEmpty()) {
            return Collections.emptyMap();
        }

        final List<byte[]> hashValues = new ArrayList<>(hashKeys.size());
        final byte[] hashesId = hashesId(type);

        try (Jedis jedis = source.getJedis()) {
            for (List<String> part : Lists.partition(hashKeys, options.getMaxHmgetSize())) {
                hashValues.addAll(jedis.hmget(hashesId, stringsToBytes(part)));
            }
        }
        if (hashValues.size() != hashKeys.size()) {
            throw new RuntimeException("Expected same size result as request");
        }
        final Map<String, byte[]> hashes = new HashMap<>(hashKeys.size());
        for (int i = 0; i < hashValues.size(); i++) {
            final byte[] hashValue = hashValues.get(i);
            if (hashValue != null) {
                hashes.put(hashKeys.get(i), hashValue);
            }
        }

        return isHashingDisabled(type) ? Collections.emptyMap() : hashes;
    }

    private CacheData extractItem(String id, List<byte[]> keyResult, List<String> knownRels) {
        if (keyResult.get(0) == null) {
            return null;
        }

        final Map<String, Object> attributes = attributesCodec.decode(keyResult.get(0));
        final Map<String, Collection<String>> relationships = new HashMap<>(keyResult.size() - 1);
        for (int relIdx = 1; relIdx < keyResult.size(); relIdx++) {
            Collection<String> rel = relationshipsCodec.decode(keyResult.get(relIdx));
            if (rel != null) {
                String relType = knownRels.get(relIdx - 1);
                relationships.put(relType, rel);
            }
        }

        return new DefaultCacheData(id, attributes, relationships);
    }

    private String attributesIdString(String type, String id) {
        return String.format("%s:%s:attributes:%s", prefix, type, id);
    }

    private byte[] attributesId(String type, String id) {
        return stringToBytes(attributesIdString(type, id));
    }

    private String relationshipIdString(String type, String id, String relationship) {
        return String.format("%s:%s:relationships:%s:%s", prefix, type, id, relationship);
    }

    private byte[] relationshipId(String type, String id, String relationship) {
        return stringToBytes(relationshipIdString(type, id, relationship));
    }

    private byte[] hashesId(String type) {
        return stringToBytes(String.format("%s:%s:hashes", prefix, type));
    }

    private String hashesDisabled(String type) {
        return String.format("%s:%s:hashes.disabled", prefix, type);
    }

    private String allRelationshipsId(String type) {
        return String.format("%s:%s:relationships", prefix, type);
    }

    private String allOfTypeId(String type) {
        return String.format("%s:%s:members", prefix, type);
    }

    private String allOfTypeReindex(String type) {
        return String.format("%s:%s:members.2", prefix, type);
    }

    /**
     * Comparator for lexical sort of byte arrays to enable a partitioning a
     * sorted map of hash keys.
     * <p>
     * This is essentially String.compareTo implemented on a byte array.
     */
    private static class ByteArrayComparator implements Comparator<byte[]>, Serializable {
        private static final long serialVersionUID = 42424242421L;
        @Override
        public int compare(byte[] v1, byte[] v2) {
            final int len1 = v1.length;
            final int len2 = v2.length;
            final int lim = Math.min(v1.length, v2.length);

            for (int i = 0; i < lim; i++) {
                byte b1 = v1[i];
                byte b2 = v2[i];
                if (b1 != b2) {
                    return b1 - b2;
                }
            }
            return len1 - len2;
        }
    }

    static class ValueCodec<T> {

        //GZIP starts with a well-known header sequence, see https://www.ietf.org/rfc/rfc1952.txt
        private static final byte GZIP_ID1 = (byte) 0x1f;
        private static final byte GZIP_ID2 = (byte) 0x8b;

        private final ObjectMapper objectMapper;
        private final boolean enableCompression;
        private final TypeReference<T> typeRef;
        private final Predicate<T> isEmpty;

        public ValueCodec(ObjectMapper objectMapper, boolean enableCompression, TypeReference<T> typeRef, Predicate<T> isEmpty) {
            this.objectMapper = objectMapper;
            this.enableCompression = enableCompression;
            this.typeRef = typeRef;
            this.isEmpty = isEmpty;
        }

        public byte[] encode(T input) {
          try {
              if (input == null || isEmpty.test(input)) {
                  return null;
              }
              byte[] json = objectMapper.writeValueAsBytes(input);
              if (!enableCompression) {
                  return json;
              }
              ByteArrayOutputStream compressed = new ByteArrayOutputStream(json.length);
              try (GZIPOutputStream gz = new GZIPOutputStream(compressed, 8 * 1024)) {
                  gz.write(json);
              }

              return compressed.toByteArray();
          } catch (Exception ex) {
              throw new RuntimeException("Attribute serialization failed", ex);
          }
        }

        public T decode(byte[] input) {
            try {
                if (input == null || input.length == 0) {
                    return null;
                }

                if (input.length < 2) {
                    throw new IllegalArgumentException("Invalid input, length = " + input.length);
                }

                if (input[0] == GZIP_ID1 && input[1] == GZIP_ID2) {
                    ByteArrayOutputStream decompressed = new ByteArrayOutputStream(4 * 1024);
                    try (GZIPInputStream gz = new GZIPInputStream(new ByteArrayInputStream(input), input.length)) {
                        byte[] buf = new byte[4 * 1024];
                        int bytesRead;
                        while ((bytesRead = gz.read(buf)) != -1) {
                            decompressed.write(buf, 0, bytesRead);
                        }
                    }
                    input = decompressed.toByteArray();
                }
                return objectMapper.readValue(input, typeRef);
            } catch (Exception ex) {
                throw new RuntimeException("Attribute deserialization failed", ex);
            }
        }
    }
}
