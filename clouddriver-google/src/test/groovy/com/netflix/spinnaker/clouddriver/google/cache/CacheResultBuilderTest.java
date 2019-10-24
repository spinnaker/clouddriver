/*
 * Copyright 2019 Google, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.google.cache;

import static com.google.common.collect.Iterables.getOnlyElement;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.clouddriver.google.cache.CacheResultBuilder.CacheDataBuilder;
import java.util.Collection;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

@RunWith(JUnitPlatform.class)
final class CacheResultBuilderTest {

  @Test
  public void testBuild() {
    CacheResultBuilder cacheResultBuilder = new CacheResultBuilder();

    CacheDataBuilder appBuilder = cacheResultBuilder.namespace("applications").keep("appKey");
    appBuilder.setAttributes(ImmutableMap.of("santa", "claus"));
    appBuilder.setRelationships(ImmutableMap.of("clusters", ImmutableList.of("clusterKey")));
    CacheDataBuilder clusterBuilder = cacheResultBuilder.namespace("clusters").keep("clusterKey");
    clusterBuilder.setAttributes(ImmutableMap.of("xmen", "wolverine"));
    clusterBuilder.setRelationships(ImmutableMap.of("foo", ImmutableList.of("bar")));

    Map<String, Collection<CacheData>> cacheResults = cacheResultBuilder.build().getCacheResults();

    assertThat(cacheResults).isNotEmpty();
    assertThat(cacheResults.get("applications")).hasSize(1);
    CacheData application = getOnlyElement(cacheResults.get("applications"));
    assertThat(application.getId()).isEqualTo("appKey");
    assertThat(application.getAttributes().get("santa")).isEqualTo("claus");
    assertThat(application.getRelationships().get("clusters")).containsExactly("clusterKey");
    assertThat(cacheResults.get("clusters")).hasSize(1);
    CacheData cluster = getOnlyElement(cacheResults.get("clusters"));
    assertThat(cluster.getId()).isEqualTo("clusterKey");
    assertThat(cluster.getAttributes().get("xmen")).isEqualTo("wolverine");
    assertThat(cluster.getRelationships().get("foo")).containsExactly("bar");
  }
}
