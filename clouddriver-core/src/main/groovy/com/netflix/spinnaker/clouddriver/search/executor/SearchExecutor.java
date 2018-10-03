/*
 * Copyright 2018 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.search.executor;

import com.netflix.spinnaker.clouddriver.search.SearchProvider;
import com.netflix.spinnaker.clouddriver.search.SearchQueryCommand;
import com.netflix.spinnaker.clouddriver.search.SearchResultSet;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Slf4j
public class SearchExecutor {
  private Integer timeout;
  private ExecutorService executor;

  SearchExecutor(SearchExecutorConfigProperties configProperties) {
    this.timeout = configProperties.getTimeout();
    this.executor = Executors.newFixedThreadPool(configProperties.getThreadPoolSize());
  }

  public List<SearchResultSet> searchAllProviders(List<SearchProvider> providers,
                                                  SearchQueryCommand searchQuery) {
    List<Callable<SearchResultSet>> searchTasks = providers.stream().
      map(p -> new SearchTask(p, searchQuery)).
      collect(Collectors.toList());
    List<Future<SearchResultSet>> resultFutures = null;
    try {
      resultFutures = executor.invokeAll(searchTasks, timeout, TimeUnit.SECONDS);
    } catch (InterruptedException ie) {
      log.error(String.format("Search for '%s' in '%s' interrupted",
                searchQuery.getQ(), searchQuery.getPlatform()), ie);
    }

    if (resultFutures == null) {
      return Collections.EMPTY_LIST;
    }
    return resultFutures.stream().map(f -> getFuture(f)).collect(Collectors.toList());
  }

  private static SearchResultSet getFuture(Future<SearchResultSet> f) {
    SearchResultSet resultSet = null;
    try {
      resultSet = f.get();
    } catch (ExecutionException | InterruptedException e) {
      log.error(String.format("Retrieving future %s failed", f), e);
    } catch (CancellationException _) {
      log.error(String.format("Retrieving result failed due to cancelled task: %s", f));
    }

    if (resultSet == null) {
      return new SearchResultSet().setTotalMatches(0).setResults(Collections.EMPTY_LIST);
    }
    return resultSet;
  }

  private static class SearchTask implements Callable<SearchResultSet> {
    private SearchProvider provider;
    private SearchQueryCommand searchQuery;

    SearchTask(SearchProvider provider, SearchQueryCommand searchQuery) {
      this.provider = provider;
      this.searchQuery = searchQuery;
    }

    public SearchResultSet call() {
      Map<String, String> filters = searchQuery
        .getFilters()
        .entrySet()
        .stream()
        .filter(e -> !provider.excludedFilters().contains(e.getKey()))
        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

      try {
        if (searchQuery.getType() != null && !searchQuery.getType().isEmpty()) {
          return provider.search(searchQuery.getQ(), searchQuery.getType(), searchQuery.getPage(),
                                 searchQuery.getPageSize(), filters);
        } else {
          return provider.search(searchQuery.getQ(), searchQuery.getPage(), searchQuery.getPageSize(), filters);
        }
      } catch (Exception e) {
        log.error(String.format("Search for '%s' in '%s' failed",
          searchQuery.getQ(), searchQuery.getPlatform()), e);
        return new SearchResultSet().setTotalMatches(0).setResults(Collections.EMPTY_LIST);
      }
    }
  }
}
