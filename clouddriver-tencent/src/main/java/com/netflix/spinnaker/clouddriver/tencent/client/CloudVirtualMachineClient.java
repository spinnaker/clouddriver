package com.netflix.spinnaker.clouddriver.tencent.client;

import com.netflix.spinnaker.clouddriver.tencent.exception.TencentOperationException;
import com.tencentcloudapi.common.exception.TencentCloudSDKException;
import com.tencentcloudapi.cvm.v20170312.CvmClient;
import com.tencentcloudapi.cvm.v20170312.models.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.CollectionUtils;

@Slf4j(topic = "CloudVirtualMachineClient")
@Data
@EqualsAndHashCode(callSuper = false)
public class CloudVirtualMachineClient extends AbstractTencentServiceClient {
  public CloudVirtualMachineClient(String secretId, String secretKey, String region) {
    super(secretId, secretKey);
    client = new CvmClient(getCred(), region, getClientProfile());
  }

  public void terminateInstances(final List<String> instanceIds) {
    try {
      final TerminateInstancesRequest request = new TerminateInstancesRequest();
      final int len = instanceIds.size();
      for (int i = 0; i < len; i += getDEFAULT_LIMIT()) {
        int count = Math.min(len, getDEFAULT_LIMIT());
        request.setInstanceIds(instanceIds.stream().skip(i).limit(count).toArray(String[]::new));
        TerminateInstancesResponse response = client.TerminateInstances(request);
        log.info("terminateInstances result {}", response);
      }
    } catch (TencentCloudSDKException e) {
      throw new TencentOperationException(e.toString());
    }
  }

  public void startInstances(final List<String> instanceIds) {
    try {
      final StartInstancesRequest request = new StartInstancesRequest();
      final int len = instanceIds.size();
      for (int i = 0; i < len; i += getDEFAULT_LIMIT()) {
        int count = Math.min(len, getDEFAULT_LIMIT());
        request.setInstanceIds(instanceIds.stream().skip(i).limit(count).toArray(String[]::new));
        StartInstancesResponse response = client.StartInstances(request);
        log.info("StartInstances result {}", response);
      }
    } catch (TencentCloudSDKException e) {
      throw new TencentOperationException(e.toString());
    }
  }

  public void stopInstances(final List<String> instanceIds) {
    try {
      final StopInstancesRequest request = new StopInstancesRequest();
      final int len = instanceIds.size();
      for (int i = 0; i < len; i += getDEFAULT_LIMIT()) {
        int count = Math.min(len, getDEFAULT_LIMIT());
        request.setInstanceIds(instanceIds.stream().skip(i).limit(count).toArray(String[]::new));
        request.setStoppedMode("KEEP_CHARGING");
        StopInstancesResponse response = client.StopInstances(request);
        log.info("StopInstances result {}", response);
      }
    } catch (TencentCloudSDKException e) {
      throw new TencentOperationException(e.toString());
    }
  }

  public void rebootInstances(final List<String> instanceIds) {
    try {
      final RebootInstancesRequest request = new RebootInstancesRequest();
      final int len = instanceIds.size();
      for (int i = 0; i < len; i += getDEFAULT_LIMIT()) {
        int count = Math.min(len, getDEFAULT_LIMIT());
        request.setInstanceIds(instanceIds.stream().skip(i).limit(count).toArray(String[]::new));
        client.RebootInstances(request);
      }
    } catch (TencentCloudSDKException e) {
      throw new TencentOperationException(e.toString());
    }
  }

  public InstanceTypeConfig[] getInstanceTypes() {
    try {
      DescribeInstanceTypeConfigsRequest request = new DescribeInstanceTypeConfigsRequest();
      DescribeInstanceTypeConfigsResponse response = client.DescribeInstanceTypeConfigs(request);
      return response.getInstanceTypeConfigSet();
    } catch (TencentCloudSDKException e) {
      throw new TencentOperationException(e.toString());
    }
  }

  public List<KeyPair> getKeyPairs() {
    return iterQuery(
        (offset, limit) -> {
          DescribeKeyPairsRequest request = new DescribeKeyPairsRequest();
          request.setOffset(offset);
          request.setLimit(limit);
          DescribeKeyPairsResponse response = client.DescribeKeyPairs(request);
          return Arrays.asList(response.getKeyPairSet());
        });
  }

  public List<Image> getImages() {
    return iterQuery(
        (offset, limit) -> {
          DescribeImagesRequest request = new DescribeImagesRequest();
          request.setOffset(offset);
          request.setLimit(limit);
          DescribeImagesResponse response = client.DescribeImages(request);
          return Arrays.asList(response.getImageSet());
        });
  }

  public List<Instance> getInstances(List<String> instanceIds) {
    return iterQuery(
        (offset, limit) -> {
          DescribeInstancesRequest request = new DescribeInstancesRequest();
          request.setOffset(offset);
          request.setLimit(limit);
          if (!CollectionUtils.isEmpty(instanceIds)) {
            int end = Math.min(offset + limit - 1, instanceIds.size() - 1);
            if (offset < end) {
              request.setInstanceIds(
                  instanceIds.stream().skip(offset).limit(end - offset).toArray(String[]::new));
            }
          }
          DescribeInstancesResponse response = client.DescribeInstances(request);
          log.info(
              "cloudVirtualMachineClient getInstances {}",
              Arrays.stream(response.getInstanceSet())
                  .map(
                      it -> {
                        return "instance" + it.getInstanceId() + "status" + it.getInstanceState();
                      }));
          return Arrays.asList(response.getInstanceSet());
        });
  }

  public List<Instance> getInstances() {
    return getInstances(new ArrayList<String>());
  }

  public final String getEndPoint() {
    return endPoint;
  }

  private final String endPoint = "cvm.tencentcloudapi.com";
  private CvmClient client;
}
