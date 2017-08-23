/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.controllers

import com.netflix.spinnaker.clouddriver.search.SearchProvider
import com.netflix.spinnaker.clouddriver.search.SearchResultSet
import org.apache.log4j.Logger
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

import javax.servlet.http.HttpServletRequest

@RestController
class SearchController {

  protected static final Logger log = Logger.getLogger(this)

  @Autowired
  List<SearchProvider> searchProviders

  /**
   * Simple search endpoint that delegates to {@link SearchProvider}s.
   * @param query the phrase to query
   * @param type (optional) a filter, used to only return results of that type. If no value is supplied, all types will be returned
   * @param platform a filter, used to only return results from providers whose platform value matches this
   * @param pageNumber the page number, starting with 1
   * @param pageSize the maximum number of results to return per page
   * @param filters (optional) a map of key-value pairs to further filter the keys
   * @return a list {@link SearchResultSet)s
   */
  @PreAuthorize("@fiatPermissionEvaluator.storeWholePermission()")
  @RequestMapping(value = '/search', method = RequestMethod.GET)
  List<SearchResultSet> search(@RequestParam(value = "q", defaultValue = "", required = false) String query,
                               @RequestParam(value = "type") List<String> type,
                               @RequestParam(value = "platform", required = false) String platform,
                               @RequestParam(value = "pageSize", defaultValue = "10000", required = false) int pageSize,
                               @RequestParam(value = "page", defaultValue = "1", required = false) int page,
                               HttpServletRequest httpServletRequest) {

    Map<String, String> filters = httpServletRequest.getParameterNames().findAll { String parameterName ->
      !["q", "type", "platform", "pageSize", "page"].contains(parameterName)
    }.collectEntries { String parameterName ->
      [parameterName, httpServletRequest.getParameter(parameterName)]
    }

    SearchQueryCommand searchQuery =
      new SearchQueryCommand(q: query, type: type, platform: platform, pageSize: pageSize, page: page, filters: filters)
    log.info("Fetching search results for ${query}, platform: ${platform}, type: ${type}, pageSize: ${pageSize}, pageNumber: ${page}, filters: ${filters}")

    List<SearchProvider> providers = searchQuery.platform ?
      searchProviders.findAll { it.platform == searchQuery.platform } :
      searchProviders

    List<SearchResultSet> results = searchAllProviders(providers, searchQuery)

    if (results.size() == 1) {
      results
    } else {

      int total = results.inject(0) { acc, item -> acc + item.totalMatches }
      List<Map<String, String>> allResults = results.inject([]) { acc, item -> acc.addAll(item.results); acc }

      //TODO-cfieber: this is a temporary workaround to https://github.com/spinnaker/deck/issues/128
      [new SearchResultSet(
        totalMatches: total,
        pageNumber: searchQuery.page,
        pageSize: searchQuery.pageSize,
        platform: 'aws', //TODO-cfieber: hardcoding this for now...
        query: searchQuery.q,
        results: allResults)]
    }
  }

  List<SearchResultSet> searchAllProviders(List<SearchProvider> providers,
                                           SearchQueryCommand searchQuery) {
    List<SearchResultSet> results = providers.collect {
      try {
        if (searchQuery.type && !searchQuery.type.isEmpty()) {
          it.search(searchQuery.q, searchQuery.type, searchQuery.page, searchQuery.pageSize, searchQuery.filters)
        } else {
          it.search(searchQuery.q, searchQuery.page, searchQuery.pageSize, searchQuery.filters)
        }
      } catch (Exception e) {
        log.debug("Search for '${searchQuery.q}' in '${it.platform}' failed", e)
        new SearchResultSet(totalMatches: 0, results: [])
      }
    }

    results
  }

  static class SearchQueryCommand {
    /**
     * the phrase to query
     */
    String q

    /**
     * (optional) a filter, used to only return results of that type. If no value is supplied, all types will be returned
     */
    List<String> type

    /**
     * a filter, used to only return results from providers whose platform value matches this
     */
    String platform = ''

    /**
     * the page number, starting with 1
     */
    Integer page = 1

    /**
     * the maximum number of results to return per page
     */
    Integer pageSize = 10

    /**
     * (optional) a map of ad-hoc key-value pairs to further filter the keys,
     * based on the map provided by {@link com.netflix.spinnaker.oort.aws.data.Keys#parse(java.lang.String)}
     * potential matches must fully intersect the filter map entries
     */
    Map<String, String> filters
  }
}
