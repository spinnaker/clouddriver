package com.netflix.spinnaker.cats.sql

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.cats.cache.Cache
import com.netflix.spinnaker.cats.cache.RelationshipCacheFilter
import com.netflix.spinnaker.cats.cache.WriteableCacheSpec
import com.netflix.spinnaker.cats.sql.cache.SqlCache
import com.netflix.spinnaker.cats.sql.cache.SqlCacheMetrics
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService
import com.netflix.spinnaker.kork.sql.config.RetryProperties
import com.netflix.spinnaker.kork.sql.config.SqlRetryProperties
import com.netflix.spinnaker.kork.sql.test.SqlTestUtil
import com.zaxxer.hikari.HikariDataSource
import org.jooq.DSLContext
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Unroll

import java.time.Clock
import java.time.Instant
import java.time.ZoneId

class SqlCacheSpec extends WriteableCacheSpec {

  @Shared
  DSLContext context

  @AutoCleanup("close")
  HikariDataSource dataSource

  def cleanup() {
    SqlTestUtil.cleanupDb(context)
  }

  def 'should handle invalid type'() {
    given:
    def data = createData('blerp', [a: 'b'])
    ((SqlCache) cache).merge('foo.bar', data)

    when:
    def retrieved = ((SqlCache) cache).getAll('foo.bar')

    then:
    retrieved.size() == 1
    retrieved.findAll { it.id == "blerp" }.size() == 1
  }

  def 'should not write an item if it is unchanged'() {
    setup:
    def data = createData('blerp', [a: 'b'])

    when:
    ((SqlCache) cache).merge('foo', data)

    then:
    1 * ((SqlCache) cache).cacheMetrics.merge('test', 'foo', 1, 1, 0, 0, 1, 1, 0)

    when:
    ((SqlCache) cache).merge('foo', data)

    then:
    // SqlCacheMetrics currently sets items to # of items stored. The redis impl
    // sets this to # of items passed to merge, regardless of how many are actually stored
    // after deduplication. TODO: Having both metrics would be nice.
    1 * ((SqlCache) cache).cacheMetrics.merge('test', 'foo', 1, 0, 0, 0, 1, 0, 0)
  }

  def 'all items are stored and retrieved when larger than sql chunk sizes'() {
    given:
    def data = (1..10).collect { createData("fnord-$it") }
    ((SqlCache) cache).mergeAll('foo', data)

    when:
    def retrieved = ((SqlCache) cache).getAll('foo')

    then:
    retrieved.size() == 10
    retrieved.findAll { it.id == "fnord-5" }.size() == 1
  }

  @Unroll
  def 'generates where clause based on cacheFilters'() {
    when:
    def relPrefixes = ((SqlCache) cache).getRelationshipFilterPrefixes(filter)
    def where = ((SqlCache) cache).getRelWhere(relPrefixes, queryPrefix)

    then:
    where == expected

    where:
    filter                                                 || queryPrefix      || expected
    RelationshipCacheFilter.none()                         || "meowdy=partner" || "meowdy=partner"
    null                                                   || "meowdy=partner" || "meowdy=partner"
    RelationshipCacheFilter.include("instances", "images") || null             || "(rel_type LIKE 'instances%' OR rel_type LIKE 'images%')"
    RelationshipCacheFilter.include("images")              || "meowdy=partner" || "meowdy=partner AND (rel_type LIKE 'images%')"
    null                                                   || null             || "1=1"
  }

  @Override
  Cache getSubject() {
    def mapper = new ObjectMapper()
    def clock = new Clock.FixedClock(Instant.EPOCH, ZoneId.of("UTC"))
    def sqlRetryProperties = new SqlRetryProperties(new RetryProperties(1, 10), new RetryProperties(1, 10))

    def dynamicConfigService = Mock(DynamicConfigService) {
      getConfig(_ as Class, _ as String, _) >> 2
    }

    SqlTestUtil.TestDatabase testDatabase = SqlTestUtil.initTcMysqlDatabase()
    context = testDatabase.context
    dataSource = testDatabase.dataSource

    return new SqlCache(
      "test",
      context,
      mapper,
      null,
      clock,
      sqlRetryProperties,
      "test",
      Mock(SqlCacheMetrics),
      dynamicConfigService
    )
  }

}
