package com.netflix.spinnaker.clouddriver.tencent.client

import com.netflix.spinnaker.clouddriver.tencent.exception.TencentOperationException
import com.tencentcloudapi.common.Credential
import com.tencentcloudapi.common.exception.TencentCloudSDKException
import com.tencentcloudapi.cvm.v20170312.CvmClient
import com.tencentcloudapi.cvm.v20170312.models.*
import groovy.util.logging.Slf4j
import org.springframework.stereotype.Component

@Slf4j
class CloudVirtualMachineClient extends AbstractTencentServiceClient {
  final String endPoint = "cvm.tencentcloudapi.com"
  private CvmClient client

  CloudVirtualMachineClient(String secretId, String secretKey, String region) {
    super(secretId, secretKey)
    client = new CvmClient(cred, region, clientProfile)
  }

  def terminateInstances(List<String> instanceIds) {
    try {
      def request = new TerminateInstancesRequest()
      def len = instanceIds.size()
      0.step len, DEFAULT_LIMIT, {
        def endIndex = Math.min len, it+DEFAULT_LIMIT
        request.instanceIds = instanceIds[it..endIndex-1]
        client.TerminateInstances(request)
      }
    } catch (TencentCloudSDKException e) {
      throw new TencentOperationException(e.toString())
    }
  }

  def rebootInstances(List<String> instanceIds) {
    try {
      def request = new RebootInstancesRequest()
      def len = instanceIds.size()
      0.step len, DEFAULT_LIMIT, {
        def endIndex = Math.min len, it+DEFAULT_LIMIT
        request.instanceIds = instanceIds[it..endIndex-1]
        client.RebootInstances(request)
      }
    } catch (TencentCloudSDKException e) {
      throw new TencentOperationException(e.toString())
    }
  }

  def getInstanceTypes() {
    try {
      def request = new DescribeInstanceTypeConfigsRequest()
      def response = client.DescribeInstanceTypeConfigs request
      response.instanceTypeConfigSet
    } catch (TencentCloudSDKException e) {
      throw new TencentOperationException(e.toString())
    }
  }

  def getKeyPairs() {
    iterQuery { offset, limit ->
      def request = new DescribeKeyPairsRequest(offset: offset, limit: limit)
      def response = client.DescribeKeyPairs request
      response.keyPairSet
    } as List<KeyPair>
  }

  def getImages() {
    iterQuery { offset, limit ->
      def request = new DescribeImagesRequest(offset: offset, limit: limit)
      def response = client.DescribeImages request
      response.imageSet
    } as List<Image>
  }

  def getInstances(List<String> instanceIds=[]) {
    iterQuery { offset, limit ->
      def request = new DescribeInstancesRequest(offset: offset, limit: limit)
      if (instanceIds) {
        def end = Math.min(offset+limit-1 as Integer, instanceIds.size()-1)
        if (offset < end) {
          request.instanceIds = instanceIds[offset..end]
        }
      }
      def response = client.DescribeInstances request
      response.instanceSet
    } as List<Instance>
  }
}
