package com.netflix.spinnaker.clouddriver.titus.deploy.handlers;

import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository;
import com.netflix.spinnaker.clouddriver.deploy.DeployDescription;
import com.netflix.spinnaker.clouddriver.deploy.DeployHandler;
import com.netflix.spinnaker.clouddriver.saga.SagaService;
import com.netflix.spinnaker.clouddriver.saga.models.Saga;
import com.netflix.spinnaker.clouddriver.saga.utils.Checksum;
import com.netflix.spinnaker.clouddriver.titus.JobType;
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
        Arrays.asList(
            Front50AppLoaded.class.getSimpleName(),
            TitusDeployCreated.class.getSimpleName(),
            TitusDeployPrepared.class.getSimpleName(),
            TitusJobSubmitted.class.getSimpleName(),
            TitusDeployCompleted.class.getSimpleName());

    if (JobType.SERVICE.value().equals(inputDescription.getJobType())) {
      requiredEvents.add(TitusLoadBalancersApplied.class.getSimpleName());
      requiredEvents.add(TitusScalingPoliciesApplied.class.getSimpleName());
    }

    // TODO(rz): compensation events
    Saga saga =
        new Saga(
            "titus://v1.CreateServerGroup",
            Optional.ofNullable(getTask().getRequestId()).orElse(getTask().getId()),
            "titusDeployHandler",
            requiredEvents,
            Collections.emptyList());

    String checksum = Checksum.md5(inputDescription);

    sagaService.save(saga);
    sagaService.apply(
        saga.getName(),
        saga.getId(),
        new TitusDeployCreated(
            saga.getName(), saga.getId(), inputDescription, priorOutputs, checksum));

    TitusDeployCompleted event =
        sagaService.get(saga.getName(), saga.getId()).getLastEvent(TitusDeployCompleted.class);

    return event.getDeploymentResult();
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
