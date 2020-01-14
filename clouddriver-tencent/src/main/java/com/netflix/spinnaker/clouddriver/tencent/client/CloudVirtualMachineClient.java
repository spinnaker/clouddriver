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
import org.springframework.util.StringUtils;

@Slf4j
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
        int endIndex = Math.min(len, i + getDEFAULT_LIMIT());
        request.setInstanceIds(
            instanceIds.stream().skip(i).limit(endIndex - 1 - i).toArray(String[]::new));
        client.TerminateInstances(request);
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
        int endIndex = Math.min(len, i + getDEFAULT_LIMIT());
        request.setInstanceIds(
            instanceIds.stream().skip(i).limit(endIndex - 1 - i).toArray(String[]::new));
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
          if (StringUtils.isEmpty(instanceIds)) {
            int end = Math.min(offset + limit - 1, instanceIds.size() - 1);
            if (offset < end) {
              request.setInstanceIds(
                  instanceIds.stream().skip(offset).limit(end - offset).toArray(String[]::new));
            }
          }
          DescribeInstancesResponse response = client.DescribeInstances(request);
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
