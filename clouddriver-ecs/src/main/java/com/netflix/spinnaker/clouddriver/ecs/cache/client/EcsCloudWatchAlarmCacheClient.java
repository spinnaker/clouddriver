/*
 * Copyright 2017 Lookout, Inc.
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

package com.netflix.spinnaker.clouddriver.ecs.cache.client;

import static com.netflix.spinnaker.clouddriver.ecs.cache.Keys.Namespace.ALARMS;

import com.netflix.spinnaker.cats.cache.Cache;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.clouddriver.ecs.cache.Keys;
import com.netflix.spinnaker.clouddriver.ecs.cache.model.EcsMetricAlarm;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class EcsCloudWatchAlarmCacheClient extends AbstractCacheClient<EcsMetricAlarm> {

  @Autowired
  public EcsCloudWatchAlarmCacheClient(Cache cacheView) {
    super(cacheView, ALARMS.toString());
  }

  @Override
  protected EcsMetricAlarm convert(CacheData cacheData) {
    EcsMetricAlarm metricAlarm = new EcsMetricAlarm();
    Map<String, Object> attributes = cacheData.getAttributes();

    metricAlarm.setAlarmArn((String) attributes.get("alarmArn"));
    metricAlarm.setAlarmName((String) attributes.get("alarmName"));
    metricAlarm.setAccountName((String) attributes.get("accountName"));
    metricAlarm.setRegion((String) attributes.get("region"));

    if (attributes.containsKey("alarmActions") && attributes.get("alarmActions") != null) {
      metricAlarm.setAlarmActions((Collection<String>) attributes.get("alarmActions"));
    } else {
      metricAlarm.setAlarmActions(Collections.emptyList());
    }

    if (attributes.containsKey("okActions") && attributes.get("okActions") != null) {
      metricAlarm.setOKActions((Collection<String>) attributes.get("okActions"));
    } else {
      metricAlarm.setOKActions(Collections.emptyList());
    }

    if (attributes.containsKey("insufficientDataActions")
        && attributes.get("insufficientDataActions") != null) {
      metricAlarm.setInsufficientDataActions(
          (Collection<String>) attributes.get("insufficientDataActions"));
    } else {
      metricAlarm.setInsufficientDataActions(Collections.emptyList());
    }

    return metricAlarm;
  }

  public List<EcsMetricAlarm> getMetricAlarms(
      String serviceName, String accountName, String region, String ecsClusterName) {
    List<EcsMetricAlarm> metricAlarms = new LinkedList<>();

    String glob = Keys.getAlarmKey(accountName, region, "*", ecsClusterName);
    Collection<String> metricAlarmsIds = filterIdentifiers(glob);
    String globEmptyDimension = Keys.getAlarmKey(accountName, region, "*", "");
    Collection<String> otherMetricAlarmsIds = filterIdentifiers(globEmptyDimension);

    Collection<String> combinedMetricIds =
        Stream.of(metricAlarmsIds, otherMetricAlarmsIds)
            .filter(m -> m != null)
            .flatMap(Collection::stream)
            .collect(Collectors.toList());

    Collection<EcsMetricAlarm> allMetricAlarms = getAll(combinedMetricIds);

    for (EcsMetricAlarm metricAlarm : allMetricAlarms) {
      if (metricAlarm.getAlarmActions().stream().anyMatch(action -> action.contains(serviceName))
          || metricAlarm.getOKActions().stream().anyMatch(action -> action.contains(serviceName))
          || metricAlarm.getInsufficientDataActions().stream()
              .anyMatch(action -> action.contains(serviceName))) {
        metricAlarms.add(metricAlarm);
      }
    }

    return metricAlarms;
  }
}
