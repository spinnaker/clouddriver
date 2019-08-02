package com.netflix.spinnaker.clouddriver.titus.deploy.handlers;

import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository;
import com.netflix.spinnaker.clouddriver.deploy.DeployDescription;
import com.netflix.spinnaker.clouddriver.deploy.DeployHandler;
import com.netflix.spinnaker.clouddriver.orchestration.events.CreateServerGroupEvent;
import com.netflix.spinnaker.clouddriver.saga.SagaService;
import com.netflix.spinnaker.clouddriver.saga.models.Saga;
import com.netflix.spinnaker.clouddriver.titus.JobType;
import com.netflix.spinnaker.clouddriver.titus.TitusCloudProvider;
import com.netflix.spinnaker.clouddriver.titus.TitusException;
import com.netflix.spinnaker.clouddriver.titus.deploy.description.TitusDeployDescription;
import com.netflix.spinnaker.clouddriver.titus.deploy.events.Front50AppLoaded;
import com.netflix.spinnaker.clouddriver.titus.deploy.events.TitusDeployCompleted;
import com.netflix.spinnaker.clouddriver.titus.deploy.events.TitusDeployCreated;
import com.netflix.spinnaker.clouddriver.titus.deploy.events.TitusDeployPrepared;
import com.netflix.spinnaker.clouddriver.titus.deploy.events.TitusJobSubmitted;
import com.netflix.spinnaker.clouddriver.titus.deploy.events.TitusLoadBalancersApplied;
import com.netflix.spinnaker.clouddriver.titus.deploy.events.TitusScalingPoliciesApplied;
import groovy.util.logging.Slf4j;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class TitusDeployHandler implements DeployHandler<TitusDeployDescription> {
  private final SagaService sagaService;

  @Autowired
  public TitusDeployHandler(SagaService sagaService) {
    this.sagaService = sagaService;
  }

  private static Task getTask() {
    return TaskRepository.threadLocalTask.get();
  }

  @Override
  public TitusDeploymentResult handle(
      final TitusDeployDescription inputDescription, List priorOutputs) {

    List<String> requiredEvents =
        new LinkedList<>(
            Arrays.asList(
                // TODO(rz): TitusDeployCreated should be emitted as result of a Command; not used
                // as a command.
                Front50AppLoaded.class.getSimpleName(),
                TitusDeployPrepared.class.getSimpleName(),
                TitusJobSubmitted.class.getSimpleName()));

    if (JobType.isEqual(inputDescription.getJobType(), JobType.SERVICE)) {
      requiredEvents.add(TitusLoadBalancersApplied.class.getSimpleName());
      requiredEvents.add(TitusScalingPoliciesApplied.class.getSimpleName());
    }

    // TODO(rz): This needs to be re-entrant: Would be nice to pass this off to a
    // "StartTitusDeployCommand" which would re-build the saga and then start from
    // the next unapplied event
    requiredEvents.add(TitusDeployCompleted.class.getSimpleName());

    // TODO(rz): compensation events
    Saga saga =
        new Saga(
            "titus.Deploy",
            Optional.ofNullable(getTask().getRequestId()).orElse(getTask().getId()),
            null,
            requiredEvents,
            Collections.emptyList());

    saga.addEvent(
        new TitusDeployCreated(saga.getName(), saga.getId(), inputDescription, priorOutputs));

    sagaService.save(saga, true);

    TitusDeployCompleted completedEvent =
        sagaService.awaitCompletion(
            saga, completedSaga -> completedSaga.getLastEvent(TitusDeployCompleted.class));

    if (completedEvent == null) {
      // TODO(rz): Gross
      throw new TitusException(
          "Failed to complete titus deployment: Did not receive completed event");
    }

    // TODO(rz): Ew, side effects...
    completedEvent
        .getDeploymentResult()
        .getServerGroupNames()
        .forEach(
            serverGroupName ->
                inputDescription
                    .getEvents()
                    .add(
                        new CreateServerGroupEvent(
                            TitusCloudProvider.ID,
                            completedEvent.getTitusAccountId(),
                            inputDescription.getRegion(),
                            serverGroupName)));

    return completedEvent.getDeploymentResult();
  }

  @Override
  public boolean handles(DeployDescription description) {
    return description instanceof TitusDeployDescription;
  }

  public static class Front50Application {
    private String email;
    private boolean platformHealthOnly;

    public String getEmail() {
      return email;
    }

    public void setEmail(String email) {
      this.email = email;
    }

    public boolean getPlatformHealthOnly() {
      return platformHealthOnly;
    }

    public void setPlatformHealthOnly(boolean platformHealthOnly) {
      this.platformHealthOnly = platformHealthOnly;
    }
  }
}
