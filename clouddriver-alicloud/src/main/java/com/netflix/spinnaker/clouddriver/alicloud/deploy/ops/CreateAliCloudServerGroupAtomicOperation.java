/*
 * Copyright 2022 Alibaba Group.
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

package com.netflix.spinnaker.clouddriver.alicloud.deploy.ops;

import com.aliyuncs.IAcsClient;
import com.aliyuncs.ess.model.v20140828.*;
import com.aliyuncs.exceptions.ClientException;
import com.aliyuncs.exceptions.ServerException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.clouddriver.alicloud.AliCloudProvider;
import com.netflix.spinnaker.clouddriver.alicloud.common.ClientFactory;
import com.netflix.spinnaker.clouddriver.alicloud.common.DateParseHelper;
import com.netflix.spinnaker.clouddriver.alicloud.deploy.AliCloudServerGroupNameResolver;
import com.netflix.spinnaker.clouddriver.alicloud.deploy.description.BasicAliCloudDeployDescription;
import com.netflix.spinnaker.clouddriver.alicloud.exception.AliCloudException;
import com.netflix.spinnaker.clouddriver.deploy.DeploymentResult;
import com.netflix.spinnaker.clouddriver.model.ClusterProvider;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import java.text.SimpleDateFormat;
import java.util.*;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.util.CollectionUtils;

@Slf4j
public class CreateAliCloudServerGroupAtomicOperation implements AtomicOperation<DeploymentResult> {

    private final List<ClusterProvider> clusterProviders;

    private final ObjectMapper objectMapper;

    private final BasicAliCloudDeployDescription description;

    private final ClientFactory clientFactory;

    public CreateAliCloudServerGroupAtomicOperation(
        BasicAliCloudDeployDescription description,
        ObjectMapper objectMapper,
        ClientFactory clientFactory,
        List<ClusterProvider> clusterProviders) {
        this.description = description;
        this.objectMapper = objectMapper;
        this.clientFactory = clientFactory;
        this.clusterProviders = clusterProviders;
    }

    @Override
    public DeploymentResult operate(List priorOutputs) {
        DeploymentResult result = new DeploymentResult();
        // create scaling group
        IAcsClient client =
            clientFactory.createClient(
                description.getRegion(),
                description.getCredentials().getAccessKeyId(),
                description.getCredentials().getAccessSecretKey());
        AliCloudServerGroupNameResolver resolver =
            new AliCloudServerGroupNameResolver(
                description.getCredentials().getName(), description.getRegion(), clusterProviders);
        String serverGroupName =
            resolver.resolveNextServerGroupName(
                description.getApplication(),
                description.getStack(),
                description.getFreeFormDetails(),
                false);
        description.setScalingGroupName(serverGroupName);
        CreateScalingGroupRequest createScalingGroupRequest =
            objectMapper.convertValue(description, CreateScalingGroupRequest.class);
        rebuildCreateScalingGroupRequest(description, createScalingGroupRequest);
        createScalingGroupRequest.setScalingGroupName(serverGroupName);
        if (!StringUtils.isEmpty(description.getVSwitchId())) {
            createScalingGroupRequest.setVSwitchId(description.getVSwitchId());
        }
        if (description.getVSwitchIds() != null) {
            createScalingGroupRequest.setVSwitchIds(description.getVSwitchIds());
        }
        CreateScalingGroupResponse createScalingGroupResponse;
        try {
            createScalingGroupResponse = client.getAcsResponse(createScalingGroupRequest);
            description.setScalingGroupId(createScalingGroupResponse.getScalingGroupId());
        } catch (ServerException e) {
            log.info(e.getMessage());
            throw new AliCloudException(e.getMessage());
        } catch (ClientException e) {
            log.info(e.getMessage());
            throw new AliCloudException(e.getMessage());
        }

        if (StringUtils.isEmpty(description.getScalingGroupId())) {
            return result;
        }

        String scalingConfigurationId = null;

        // create scaling configuration
        for (CreateScalingConfigurationRequest scalingConfiguration :
            description.getScalingConfigurations()) {
            CreateScalingConfigurationRequest configurationRequest =
                objectMapper.convertValue(scalingConfiguration, CreateScalingConfigurationRequest.class);
            configurationRequest.setScalingGroupId(description.getScalingGroupId());
            CreateScalingConfigurationResponse configurationResponse;
            try {
                configurationResponse = client.getAcsResponse(configurationRequest);
                scalingConfigurationId = configurationResponse.getScalingConfigurationId();
            } catch (ServerException e) {
                log.info(e.getMessage());
                throw new AliCloudException(e.getMessage());
            } catch (ClientException e) {
                log.info(e.getMessage());
                throw new AliCloudException(e.getMessage());
            }
        }

        if (StringUtils.isEmpty(scalingConfigurationId)) {
            return result;
        }

        EnableScalingGroupRequest enableScalingGroupRequest = new EnableScalingGroupRequest();
        enableScalingGroupRequest.setScalingGroupId(description.getScalingGroupId());
        enableScalingGroupRequest.setActiveScalingConfigurationId(scalingConfigurationId);
        EnableScalingGroupResponse enableScalingGroupResponse;
        try {
            enableScalingGroupResponse = client.getAcsResponse(enableScalingGroupRequest);
        } catch (ServerException e) {
            log.info(e.getMessage());
            throw new AliCloudException(e.getMessage());
        } catch (ClientException e) {
            log.info(e.getMessage());
            throw new AliCloudException(e.getMessage());
        }

        this.copySourceServerGroupRelatedResource(description);

        buildResult(description, result);

        return result;
    }

    private void rebuildCreateScalingGroupRequest(
        BasicAliCloudDeployDescription description, CreateScalingGroupRequest request) {
        if (description != null
            && description.getSource() != null
            && description.getSource().getUseSourceCapacity() != null
            && description.getSource().getUseSourceCapacity()
            && StringUtils.isNotEmpty(description.getSource().getAsgName())) {

            String asgName = description.getSource().getAsgName();
            DescribeScalingGroupsRequest describeScalingGroupsRequest =
                new DescribeScalingGroupsRequest();
            describeScalingGroupsRequest.setScalingGroupName(asgName);
            DescribeScalingGroupsResponse describeScalingGroupsResponse;
            try {
                IAcsClient client =
                    clientFactory.createClient(
                        description.getRegion(),
                        description.getCredentials().getAccessKeyId(),
                        description.getCredentials().getAccessSecretKey());

                describeScalingGroupsResponse = client.getAcsResponse(describeScalingGroupsRequest);
                if (describeScalingGroupsResponse.getScalingGroups().size() == 0) {
                    throw new AliCloudException("Old server group is does not exist");
                }
                DescribeScalingGroupsResponse.ScalingGroup scalingGroup =
                    describeScalingGroupsResponse.getScalingGroups().get(0);
                if (scalingGroup.getMaxSize() != null) {
                    request.setMaxSize(scalingGroup.getMaxSize());
                }
                if (scalingGroup.getMinSize() != null) {
                    request.setMinSize(scalingGroup.getMinSize());
                }
            } catch (Exception e) {
                log.info(e.getMessage());
                throw new AliCloudException(e.getMessage());
            }
        }
    }

    private void buildResult(BasicAliCloudDeployDescription description, DeploymentResult result) {

        List<String> serverGroupNames = new ArrayList<>();
        serverGroupNames.add(description.getRegion() + ":" + description.getScalingGroupName());
        result.setServerGroupNames(serverGroupNames);

        Map<String, String> serverGroupNameByRegion = new HashMap<>();
        serverGroupNameByRegion.put(description.getRegion(), description.getScalingGroupName());
        result.setServerGroupNameByRegion(serverGroupNameByRegion);

        Set<DeploymentResult.Deployment> deployments = new HashSet<>();

        DeploymentResult.Deployment.Capacity capacity = new DeploymentResult.Deployment.Capacity();
        capacity.setMax(description.getMaxSize());
        capacity.setMin(description.getMinSize());
        capacity.setDesired(description.getMinSize());

        DeploymentResult.Deployment deployment = new DeploymentResult.Deployment();
        deployment.setCloudProvider(AliCloudProvider.ID);
        deployment.setAccount(description.getCredentials().getName());
        deployment.setCapacity(capacity);
        deployment.setLocation(description.getRegion());
        deployment.setServerGroupName(description.getScalingGroupName());

        deployments.add(deployment);
        result.setDeployments(deployments);
    }

    private void copySourceServerGroupRelatedResource(BasicAliCloudDeployDescription description) {
        String sourceScalingGroupId = "";
        String destScalingGroupId = description.getScalingGroupId();
        String region = description.getRegion();

        String asgName = description.getSource().getAsgName();
        DescribeScalingGroupsRequest describeScalingGroupsRequest = new DescribeScalingGroupsRequest();
        describeScalingGroupsRequest.setScalingGroupName(asgName);
        DescribeScalingGroupsResponse describeScalingGroupsResponse;
        try {
            IAcsClient client =
                clientFactory.createClient(
                    description.getRegion(),
                    description.getCredentials().getAccessKeyId(),
                    description.getCredentials().getAccessSecretKey());

            describeScalingGroupsResponse = client.getAcsResponse(describeScalingGroupsRequest);
            if (describeScalingGroupsResponse.getScalingGroups().size() == 0) {
                log.info("Old server group is does not exist");
                return;
            }

            DescribeScalingGroupsResponse.ScalingGroup scalingGroup =
                describeScalingGroupsResponse.getScalingGroups().get(0);
            sourceScalingGroupId = scalingGroup.getScalingGroupId();

            if (StringUtils.isEmpty(sourceScalingGroupId) || StringUtils.isEmpty(destScalingGroupId)) {
                return;
            }

            int pageSize = 50;
            int pageNumber = 1;
            DescribeScalingRulesRequest describeScalingRulesRequest = new DescribeScalingRulesRequest();
            describeScalingRulesRequest.setSysRegionId(region);
            describeScalingRulesRequest.setScalingGroupId(sourceScalingGroupId);
            describeScalingRulesRequest.setPageSize(pageSize);
            describeScalingRulesRequest.setPageNumber(pageNumber);
            DescribeScalingRulesResponse describeScalingRulesResponse =
                client.getAcsResponse(describeScalingRulesRequest);
            Map<String, String> scalingRuleAriMap = new HashMap<>();

            while (describeScalingRulesResponse != null
                && !CollectionUtils.isEmpty(describeScalingRulesResponse.getScalingRules())) {
                for (DescribeScalingRulesResponse.ScalingRule scalingRule :
                    describeScalingRulesResponse.getScalingRules()) {
                    // scaling rule
                    CreateScalingRuleRequest createScalingRuleRequest =
                        objectMapper.convertValue(scalingRule, CreateScalingRuleRequest.class);
                    createScalingRuleRequest.setScalingGroupId(destScalingGroupId);
                    createScalingRuleRequest.setScalingRuleName("");
                    CreateScalingRuleResponse createScalingRuleResponse =
                        client.getAcsResponse(createScalingRuleRequest);
                    if (createScalingRuleResponse != null
                        && StringUtils.isNotEmpty(createScalingRuleResponse.getScalingRuleAri())) {
                        scalingRuleAriMap.put(
                            scalingRule.getScalingRuleAri(), createScalingRuleResponse.getScalingRuleAri());
                        // scheduled task
                        DescribeScheduledTasksRequest describeScheduledTasksRequest =
                            new DescribeScheduledTasksRequest();
                        describeScheduledTasksRequest.setSysRegionId(region);
                        describeScheduledTasksRequest.setScheduledAction1(scalingRule.getScalingRuleAri());
                        describeScheduledTasksRequest.setPageSize(pageSize);
                        int taskPageNumber = 1;
                        describeScheduledTasksRequest.setPageNumber(taskPageNumber);
                        DescribeScheduledTasksResponse describeScheduledTasksResponse =
                            client.getAcsResponse(describeScheduledTasksRequest);

                        while (describeScheduledTasksResponse != null
                            && !CollectionUtils.isEmpty(describeScheduledTasksResponse.getScheduledTasks())) {
                            for (DescribeScheduledTasksResponse.ScheduledTask scheduledTask :
                                describeScheduledTasksResponse.getScheduledTasks()) {
                                CreateScheduledTaskRequest createScheduledTaskRequest =
                                    objectMapper.convertValue(scheduledTask, CreateScheduledTaskRequest.class);

                                String launchTime = createScheduledTaskRequest.getLaunchTime();
                                if (launchTime != null) {
                                    Date launchTimeDate = DateParseHelper.parseUTCTime(launchTime);
                                    Calendar launchTimeCalendar = Calendar.getInstance();
                                    launchTimeCalendar.setTime(new Date());
                                    Calendar oldCalendar = Calendar.getInstance();
                                    oldCalendar.setTime(launchTimeDate);
                                    if (launchTimeDate.before(new Date())) {
                                        launchTimeCalendar.set(Calendar.MINUTE, oldCalendar.get(Calendar.MINUTE));
                                        launchTimeCalendar.set(
                                            Calendar.HOUR_OF_DAY, oldCalendar.get(Calendar.HOUR_OF_DAY));
                                        if (launchTimeCalendar.getTime().before(new Date())) {
                                            launchTimeCalendar.add(Calendar.DATE, 1);
                                        }
                                        createScheduledTaskRequest.setLaunchTime(DateParseHelper.format(launchTimeCalendar.getTime()));
                                    }
                                }
                                createScheduledTaskRequest.setSysRegionId(region);
                                createScheduledTaskRequest.setScheduledAction(
                                    createScalingRuleResponse.getScalingRuleAri());
                                createScheduledTaskRequest.setScheduledTaskName("");
                                CreateScheduledTaskResponse createScheduledTaskResponse =
                                    client.getAcsResponse(createScheduledTaskRequest);
                            }

                            taskPageNumber = taskPageNumber + 1;
                            describeScheduledTasksRequest.setPageNumber(taskPageNumber);
                            describeScheduledTasksResponse = client.getAcsResponse(describeScheduledTasksRequest);
                        }
                    }
                }
                pageNumber = pageNumber + 1;
                describeScalingRulesRequest.setPageNumber(pageNumber);
                describeScalingRulesResponse = client.getAcsResponse(describeScalingRulesRequest);
            }

            // notification configuration
            DescribeNotificationConfigurationsRequest describeNotificationConfigurationsRequest =
                new DescribeNotificationConfigurationsRequest();
            describeNotificationConfigurationsRequest.setScalingGroupId(sourceScalingGroupId);
            DescribeNotificationConfigurationsResponse describeNotificationConfigurationsResponse =
                client.getAcsResponse(describeNotificationConfigurationsRequest);
            if (describeNotificationConfigurationsResponse != null
                && !CollectionUtils.isEmpty(
                describeNotificationConfigurationsResponse.getNotificationConfigurationModels())) {
                for (DescribeNotificationConfigurationsResponse.NotificationConfigurationModel
                    notificationConfigurationModel :
                    describeNotificationConfigurationsResponse.getNotificationConfigurationModels()) {
                    CreateNotificationConfigurationRequest createNotificationConfigurationRequest =
                        objectMapper.convertValue(
                            notificationConfigurationModel, CreateNotificationConfigurationRequest.class);
                    createNotificationConfigurationRequest.setScalingGroupId(destScalingGroupId);
                    CreateNotificationConfigurationResponse createNotificationConfigurationResponse =
                        client.getAcsResponse(createNotificationConfigurationRequest);
                }
            }

            // lifecycle hooks
            DescribeLifecycleHooksRequest describeLifecycleHooksRequest =
                new DescribeLifecycleHooksRequest();
            describeLifecycleHooksRequest.setScalingGroupId(sourceScalingGroupId);
            describeLifecycleHooksRequest.setPageSize(pageSize);
            int hooksPageNumber = 1;
            describeLifecycleHooksRequest.setPageNumber(hooksPageNumber);
            DescribeLifecycleHooksResponse describeLifecycleHooksResponse =
                client.getAcsResponse(describeLifecycleHooksRequest);

            while (describeLifecycleHooksResponse != null
                && !CollectionUtils.isEmpty(describeLifecycleHooksResponse.getLifecycleHooks())) {
                for (DescribeLifecycleHooksResponse.LifecycleHook lifecycleHook :
                    describeLifecycleHooksResponse.getLifecycleHooks()) {
                    CreateLifecycleHookRequest createLifecycleHookRequest =
                        objectMapper.convertValue(lifecycleHook, CreateLifecycleHookRequest.class);
                    createLifecycleHookRequest.setScalingGroupId(destScalingGroupId);
                    CreateLifecycleHookResponse createLifecycleHookResponse =
                        client.getAcsResponse(createLifecycleHookRequest);
                }
                hooksPageNumber = hooksPageNumber + 1;
                describeLifecycleHooksRequest.setPageNumber(hooksPageNumber);
                describeLifecycleHooksResponse = client.getAcsResponse(describeLifecycleHooksRequest);
            }

            // alarm
            DescribeAlarmsRequest describeAlarmsRequest = new DescribeAlarmsRequest();
            describeAlarmsRequest.setSysRegionId(region);
            describeAlarmsRequest.setScalingGroupId(sourceScalingGroupId);
            describeAlarmsRequest.setPageSize(pageSize);
            int alarmPageNumber = 1;
            describeAlarmsRequest.setPageNumber(alarmPageNumber);
            DescribeAlarmsResponse describeAlarmsResponse = client.getAcsResponse(describeAlarmsRequest);

            while (describeAlarmsResponse != null
                && !CollectionUtils.isEmpty(describeAlarmsResponse.getAlarmList())) {
                for (DescribeAlarmsResponse.Alarm alarm : describeAlarmsResponse.getAlarmList()) {
                    CreateAlarmRequest createAlarmRequest =
                        objectMapper.convertValue(alarm, CreateAlarmRequest.class);
                    createAlarmRequest.setSysRegionId(region);
                    createAlarmRequest.setScalingGroupId(destScalingGroupId);
                    if (!CollectionUtils.isEmpty(createAlarmRequest.getDimensions())) {
                        for (CreateAlarmRequest.Dimension dimension : createAlarmRequest.getDimensions()) {
                            if (sourceScalingGroupId.equals(dimension.getDimensionValue())) {
                                dimension.setDimensionValue(destScalingGroupId);
                            }
                        }
                    }
                    createAlarmRequest.setDimensions(createAlarmRequest.getDimensions());
                    if (!CollectionUtils.isEmpty(createAlarmRequest.getAlarmActions())) {
                        List<String> alarmActions = new ArrayList<>();
                        for (String alarmAction : createAlarmRequest.getAlarmActions()) {
                            if (StringUtils.isNotEmpty(scalingRuleAriMap.get(alarmAction))) {
                                alarmActions.add(scalingRuleAriMap.get(alarmAction));
                            } else {
                                alarmActions.add(alarmAction);
                            }
                        }
                        createAlarmRequest.setAlarmActions(alarmActions);
                        CreateAlarmResponse createAlarmResponse = client.getAcsResponse(createAlarmRequest);
                        if (createAlarmResponse != null
                            && StringUtils.isNotEmpty(createAlarmResponse.getAlarmTaskId())
                            && alarm.getEnable() != null
                            && alarm.getEnable()) {
                            EnableAlarmRequest enableAlarmRequest = new EnableAlarmRequest();
                            enableAlarmRequest.setSysRegionId(region);
                            enableAlarmRequest.setAlarmTaskId(createAlarmResponse.getAlarmTaskId());
                            EnableAlarmResponse enableAlarmResponse = client.getAcsResponse(enableAlarmRequest);
                        }
                    }
                }
                alarmPageNumber = alarmPageNumber + 1;
                describeAlarmsRequest.setPageNumber(alarmPageNumber);
                describeAlarmsResponse = client.getAcsResponse(describeAlarmsRequest);
            }

        } catch (Exception e) {
            log.info(e.getMessage());
            return;
        }
    }
}
