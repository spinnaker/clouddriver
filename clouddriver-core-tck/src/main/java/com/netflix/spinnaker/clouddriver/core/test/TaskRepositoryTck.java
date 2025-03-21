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
package com.netflix.spinnaker.clouddriver.core.test;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.netflix.spinnaker.clouddriver.core.ClouddriverHostname;
import com.netflix.spinnaker.clouddriver.data.task.Status;
import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository;
import java.lang.reflect.InvocationTargetException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.commons.lang3.text.WordUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public abstract class TaskRepositoryTck<T extends TaskRepository> {

  protected TaskRepository subject;

  protected abstract T createTaskRepository();

  @BeforeEach
  public void setupTest() {
    subject = createTaskRepository();
  }

  @Test
  public void testTaskPersistence() {
    Task t1 = subject.create("TEST", "Test Status");
    Task t2 = subject.create("TEST", "Test Status");

    assertThat(t1.getId()).isNotEqualTo(t2.getId());
  }

  @Test
  public void testTaskLookup() {
    Task t1 = subject.create("TEST", "Test Status");
    Task t2 = subject.get(t1.getId());

    assertThat(t1.getId()).isEqualTo(t2.getId());
    assertThat(t1.getStatus().getStatus()).isEqualTo(t2.getStatus().getStatus());
    assertThat(t1.getStatus().getPhase()).isEqualTo(t2.getStatus().getPhase());
    assertThat(t1.getStartTimeMs()).isEqualTo(t2.getStartTimeMs());
    assertThat(t1.getStatus().isCompleted()).isEqualTo(t2.getStatus().isCompleted());
    assertThat(t1.getStatus().isFailed()).isEqualTo(t2.getStatus().isFailed());
    assertThat(t1.getStatus().isCompleted()).isFalse();
    assertThat(t1.getStatus().isFailed()).isFalse();
    assertThat(t1.getStatus().isRetryable()).isFalse();
  }

  @Test
  public void testFailureStatus() {
    Task t1 = subject.create("TEST", "Test Status");
    t1.fail();

    Task t2 = subject.get(t1.getId());

    assertThat(t2.getStatus().isCompleted()).isTrue();
    assertThat(t2.getStatus().isFailed()).isTrue();
    assertThat(t2.getStatus().isRetryable()).isFalse();
  }

  @Test
  public void testRetryableStatus() {
    Task t1 = subject.create("TEST", "Test Status");
    t1.fail(true);

    Task t2 = subject.get(t1.getId());

    assertThat(t2.getStatus().isCompleted()).isTrue();
    assertThat(t2.getStatus().isFailed()).isTrue();
    assertThat(t2.getStatus().isRetryable()).isTrue();
  }

  @Test
  public void testTaskCompletion() {
    Task t1 = subject.create("TEST", "Test Status");
    t1.updateStatus("Orchestration", "completed");
    t1.complete();

    assert (t1.getStatus().isCompleted());
  }

  @Test
  public void testListRunningTasks() {
    Task t1 = subject.create("TEST", "Test Status");
    Task t2 = subject.create("TEST", "Test Status");

    List<Task> list = subject.list();

    assertThat(list.stream().map(Task::getId)).contains(t1.getId(), t2.getId());

    t1.complete();

    assertThat(subject.list().stream().map(Task::getId).collect(Collectors.toList()))
        .doesNotContain(t1.getId());
    assertThat(subject.list().stream().map(Task::getId).collect(Collectors.toList()))
        .contains(t2.getId());
  }

  @Test
  public void testListByThisInstance() {
    Task t1 = subject.create("Test", "STARTED");
    Task t2 = subject.create("Test", "STARTED");
    Task t3 = subject.create("Test", "STARTED");
    Task t4 = subject.create("Test", "STARTED");
    Task t5 = subject.create("Test", "STARTED");
    String ownerId = ClouddriverHostname.ID;

    t3.updateOwnerId("foo@not_this_clouddriver", "Test");
    t5.complete();

    List<Task> runningTasks = subject.listByThisInstance();

    assertThat(runningTasks.stream().allMatch(t -> t.getOwnerId().equals(ownerId))).isTrue();
    assertThat(runningTasks.stream().map(Task::getId).collect(Collectors.toList()))
        .contains(t1.getId(), t2.getId(), t4.getId());
    // Task 3 doesn't belong to this pod and task 5 is not running, so should not be included in the
    // result
    assertThat(runningTasks.stream().map(Task::getId).collect(Collectors.toList()))
        .doesNotContain(t3.getId(), t5.getId());
  }

  @Test
  public void testResultObjectsPersistence() {
    Task t1 = subject.create("Test", "Test Status");

    final TestObject obj = new TestObject("blimp", "bah");

    t1.addResultObjects(Collections.singletonList(obj));

    assertThat(t1.getResultObjects()).hasSize(1);
    assertThat(getField(t1.getResultObjects().get(0), "name")).isEqualTo("blimp");
    assertThat(getField(t1.getResultObjects().get(0), "value")).isEqualTo("bah");

    t1.addResultObjects(Collections.singletonList(new TestObject("t1", "h2")));

    assertThat(t1.getResultObjects()).hasSize(2);
  }

  @Test
  public void testResultObjectOrderingIsPreserved() {
    Task t1 = subject.create("Test", "Test Status");

    t1.addResultObjects(Collections.singletonList(new TestObject("Object0", "value")));
    t1.addResultObjects(Collections.singletonList(new TestObject("Object1", "value")));
    t1.addResultObjects(Collections.singletonList(new TestObject("Object2", "value")));
    t1.addResultObjects(Collections.singletonList(new TestObject("Object3", "value")));

    assertThat(
            t1.getResultObjects().stream()
                .map(o -> getField(o, "name"))
                .collect(Collectors.toList()))
        .containsSequence("Object0", "Object1", "Object2", "Object3");
  }

  @Test
  public void testTaskHistoryPersistence() {
    Task t1 = subject.create("Test", "Test Status");
    List<? extends Status> history = t1.getHistory();

    assertThat(history).hasSize(1);

    t1.updateStatus("Orchestration", "started");

    assertThat(t1.getHistory()).hasSize(2);

    Status newEntry = t1.getHistory().get(1);
    assertThat(newEntry.getClass().getSimpleName()).isEqualTo("TaskDisplayStatus");
    assertThat(newEntry.getPhase()).isEqualTo("Orchestration");
    assertThat(newEntry.getStatus()).isEqualTo("started");

    t1.updateStatus("Orchestration", "update 0");
    t1.updateStatus("Orchestration", "update 1");
    t1.updateStatus("Orchestration", "update 2");

    assertThat(t1.getHistory()).hasSize(5);
  }

  @Test
  public void testClientRequestIdLookup() {
    Task t1 = subject.create("Test", "Test Status", "the-key");
    Task t2 = subject.create("Test", "Test Status 2", "the-key");
    Task t3 = subject.create("Test", "Test Status 3", "other-key");

    assertThat(t1.getId()).isEqualTo(t2.getId());
    assertThat(t1.getId()).isNotEqualTo(t3.getId());
  }

  @Test
  public void testUpdateOwnerIdWithANewOwnerId() {
    Task t1 = subject.create("Test", "Test Status", "the-key");
    String newOwnerId = "1234@spin-clouddriver-pod-new";
    assertThat(t1.getOwnerId()).isNotEqualTo(newOwnerId);
    t1.updateOwnerId(newOwnerId, "ORCHESTRATION");
    assertThat(t1.getOwnerId()).isEqualTo(newOwnerId);
  }

  @Test
  public void testUpdateOwnerIdWithSameOwnerId() {
    Task t1 = subject.create("Test", "Test Status", "the-key");
    String newOwnerId = ClouddriverHostname.ID;
    assertThat(t1.getOwnerId()).isEqualTo(newOwnerId);
    t1.updateOwnerId(newOwnerId, "ORCHESTRATION");
    assertThat(t1.getOwnerId()).isEqualTo(newOwnerId);
  }

  @Test
  public void testTaskOutputStatus() {
    Task t1 = subject.create("Test", "Test Status");

    t1.updateOutput("some-manifest", "Deploy K8s Manifest", "output", "");

    assertThat(t1.getOutputs()).hasSize(1);
    assertThat(getField(t1.getOutputs().get(0), "manifest")).isEqualTo("some-manifest");
    assertThat(getField(t1.getOutputs().get(0), "phase")).isEqualTo("Deploy K8s Manifest");
    assertThat(getField(t1.getOutputs().get(0), "stdOut")).isEqualTo("output");
    assertThat(getField(t1.getOutputs().get(0), "stdError")).isEqualTo("");
  }

  @Test
  public void testTaskOutputStatusWithNullValues() {
    Task t1 = subject.create("Test", "Test Status");

    t1.updateOutput("some-manifest", "Deploy K8s Manifest", null, "");

    assertThat(t1.getOutputs()).hasSize(1);
    assertThat(getField(t1.getOutputs().get(0), "manifest")).isEqualTo("some-manifest");
    assertThat(getField(t1.getOutputs().get(0), "phase")).isEqualTo("Deploy K8s Manifest");
    assertThat(getField(t1.getOutputs().get(0), "stdOut")).isNull();
    assertThat(getField(t1.getOutputs().get(0), "stdError")).isEqualTo("");
  }

  public class TestObject {
    public String name;
    public String value;

    @JsonCreator
    public TestObject(String name, String value) {
      this.name = name;
      this.value = value;
    }

    public String getName() {
      return name;
    }

    public String getValue() {
      return value;
    }
  }

  private Object getField(Object object, String fieldName) {
    // TODO rz - Turns out the Redis & InMemory implementations behave totally different
    // in how they handle result objects. For now, the TCK is going to support the two
    // conflicting styles, but this needs to be fixed. Based on usage within tests, it
    // seems we expect result objects to be the actual objects, but Redis deserializes
    // as maps only. This is really more of a problem between JedisTask and DefaultTask.
    if (object instanceof Map) {
      return ((Map) object).get(fieldName);
    } else {
      try {
        return object
            .getClass()
            .getDeclaredMethod("get" + WordUtils.capitalize(fieldName))
            .invoke(object);
      } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
        throw new RuntimeException(e);
      }
    }
  }
}
