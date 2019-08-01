package com.netflix.spinnaker.clouddriver.titus.deploy.handlers;

import static java.lang.String.format;

import com.netflix.spinnaker.clouddriver.deploy.DeploymentResult;
import com.netflix.spinnaker.clouddriver.titus.JobType;
import com.netflix.spinnaker.clouddriver.titus.deploy.events.TitusJobSubmitted;
import java.util.Collections;
import java.util.List;

public class TitusDeploymentResult extends DeploymentResult {

  private String jobUri;

  public String getJobUri() {
    return jobUri;
  }

  public void setJobUri(String jobUri) {
    this.jobUri = jobUri;
  }

  public static TitusDeploymentResult from(TitusJobSubmitted event, List<String> messages) {
    TitusDeploymentResult result = new TitusDeploymentResult();

    if (JobType.isEqual(event.getDescription().getJobType(), JobType.SERVICE)) {
      forServiceJob(result, event);
    } else {
      forBatchJob(result, event);
    }

    result.setJobUri(event.getJobUri());
    result.setMessages(messages);

    return result;
  }

  /** Batch jobs use the "deployedNames" fields of the deployment result. */
  private static void forBatchJob(TitusDeploymentResult result, TitusJobSubmitted event) {
    final String region = event.getDescription().getRegion();
    final String jobUri = event.getJobUri();
    result.setDeployedNames(Collections.singletonList(jobUri));
    result.setDeployedNamesByLocation(
        Collections.singletonMap(region, Collections.singletonList(jobUri)));
  }

  /** Service jobs use the "serverGroupNames" fields for the deployment result. */
  private static void forServiceJob(TitusDeploymentResult result, TitusJobSubmitted event) {
    final String region = event.getDescription().getRegion();
    final String serverGroupName = event.getNextServerGroupName();
    result.setServerGroupNames(Collections.singletonList(format("%s:%s", region, serverGroupName)));
    result.setServerGroupNameByRegion(Collections.singletonMap(region, serverGroupName));
  }
}
