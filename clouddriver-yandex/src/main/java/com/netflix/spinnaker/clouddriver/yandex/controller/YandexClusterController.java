/*
 * Copyright 2020 YANDEX LLC
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

package com.netflix.spinnaker.clouddriver.yandex.controller;

import static yandex.cloud.api.compute.v1.instancegroup.InstanceGroupOuterClass.LogRecord;
import static yandex.cloud.api.compute.v1.instancegroup.InstanceGroupServiceOuterClass.ListInstanceGroupLogRecordsRequest;
import static yandex.cloud.api.compute.v1.instancegroup.InstanceGroupServiceOuterClass.ListInstanceGroupLogRecordsResponse;

import com.netflix.spinnaker.clouddriver.security.AccountCredentials;
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider;
import com.netflix.spinnaker.clouddriver.yandex.model.YandexCloudServerGroup;
import com.netflix.spinnaker.clouddriver.yandex.provider.view.YandexClusterProvider;
import com.netflix.spinnaker.clouddriver.yandex.security.YandexCloudCredentials;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(
    "/applications/{application}/clusters/{account}/{clusterName}/yandex/serverGroups/{serverGroupName}")
@Data
// todo!
public class YandexClusterController {
  private final AccountCredentialsProvider accountCredentialsProvider;
  private final YandexClusterProvider yandexClusterProvider;

  @Autowired
  public YandexClusterController(
      AccountCredentialsProvider accountCredentialsProvider,
      YandexClusterProvider yandexClusterProvider) {
    this.accountCredentialsProvider = accountCredentialsProvider;
    this.yandexClusterProvider = yandexClusterProvider;
  }

  @RequestMapping(value = "/scalingActivities", method = RequestMethod.GET)
  public ResponseEntity<List<Activity>> getScalingActivities(
      @PathVariable String account,
      @PathVariable String serverGroupName,
      @RequestParam(value = "region", required = true) String region) {
    AccountCredentials credentials = accountCredentialsProvider.getCredentials(account);
    if (!(credentials instanceof YandexCloudCredentials)) {
      return ResponseEntity.badRequest().build();
    }

    YandexCloudServerGroup serverGroup =
        yandexClusterProvider.getServerGroup(account, region, serverGroupName);
    if (serverGroup == null) {
      return ResponseEntity.notFound().build();
    }

    ListInstanceGroupLogRecordsResponse response =
        ((YandexCloudCredentials) credentials)
            .instanceGroupService()
            .listLogRecords(
                ListInstanceGroupLogRecordsRequest.newBuilder()
                    .setInstanceGroupId(serverGroup.getId())
                    .build());
    List<Activity> activities = new ArrayList<>();
    for (int i = 0; i < response.getLogRecordsCount(); i++) {
      LogRecord logRecord = response.getLogRecords(i);
      activities.add(
          new Activity(
              "details",
              logRecord.getMessage(),
              "cause: " + logRecord.getMessage(),
              "Successful",
              Instant.ofEpochSecond(logRecord.getTimestamp().getSeconds())));

      /*
            private groupActivities(activities: IRawScalingActivity[]): void {
        const grouped: any = _.groupBy(activities, 'cause'),
          results: IScalingEventSummary[] = [];

        _.forOwn(grouped, (group: IRawScalingActivity[]) => {
          if (group.length) {
            const events: IScalingEvent[] = [];
            group.forEach((entry: any) => {
              let availabilityZone = JSON.parse(entry.details)['Availability Zone'] || 'unknown';
              events.push({ description: entry.description, availabilityZone });
            });
            results.push({
              cause: group[0].cause,
              events,
              startTime: group[0].startTime,
              statusCode: group[0].statusCode,
              isSuccessful: group[0].statusCode === 'Successful',
            });
          }
        });
        this.activities = _.sortBy(results, 'startTime').reverse();
      }

           */
    }

    return ResponseEntity.ok(activities);
  }

  @Value
  public class Activity {
    private String details;
    private String description;
    private String cause;
    private String statusCode;
    private Instant startTime;
  }
}
