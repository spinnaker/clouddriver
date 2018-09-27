/*
 * Copyright 2018 Netflix, Inc.
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
package com.netflix.spinnaker.clouddriver.data.task;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

import static java.lang.String.format;

public class DualTaskRepository implements TaskRepository {

  private final static Logger log = LoggerFactory.getLogger(DualTaskRepository.class);

  private final TaskRepository primary;
  private final TaskRepository previous;

  public DualTaskRepository(String primaryClass,
                            String previousClass,
                            List<TaskRepository> allRepositories) {
    allRepositories.forEach(r -> log.info("Available TaskRepository: {}", r.getClass().getSimpleName()));

    primary = findTaskRepositoryByClass(primaryClass, allRepositories);
    previous = findTaskRepositoryByClass(previousClass, allRepositories);

    log.info(
      "Selected primary: {}, previous: {}",
      primary.getClass().getSimpleName(),
      previous.getClass().getSimpleName()
    );
  }

  private static TaskRepository findTaskRepositoryByClass(String className, List<TaskRepository> allRepositories) {
    Class<?> repositoryClass;
    try {
      repositoryClass = Class.forName(className);
    } catch (ClassNotFoundException e) {
      throw new IllegalStateException(format("No class found for %s", className), e);
    }
    return allRepositories.stream()
      .filter(repositoryClass::isInstance)
      .findFirst()
      .orElseThrow(() -> new IllegalStateException(format("No TaskRepository bean of class %s found", className)));
  }

  @Override
  public Task create(String phase, String status) {
    return primary.create(phase, status);
  }

  @Override
  public Task create(String phase, String status, String clientRequestId) {
    return primary.create(phase, status, clientRequestId);
  }

  @Override
  public Task get(String id) {
    return Optional.ofNullable(primary.get(id)).orElse(previous.get(id));
  }

  @Override
  public Task getByClientRequestId(String clientRequestId) {
    return Optional
      .ofNullable(primary.getByClientRequestId(clientRequestId))
      .orElse(previous.getByClientRequestId(clientRequestId));
  }

  @Override
  public List<Task> list() {
    List<Task> tasks = primary.list();
    tasks.addAll(previous.list());
    return tasks;
  }

  @Override
  public List<Task> listByThisInstance() {
    return primary.listByThisInstance();
  }
}
