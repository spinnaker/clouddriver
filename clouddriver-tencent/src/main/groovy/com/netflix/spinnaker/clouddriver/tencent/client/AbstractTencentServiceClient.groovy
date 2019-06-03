package com.netflix.spinnaker.clouddriver.tencent.client

import com.netflix.spinnaker.clouddriver.tencent.exception.TencentOperationException
import com.tencentcloudapi.common.AbstractModel
import com.tencentcloudapi.common.Credential
import com.tencentcloudapi.common.exception.TencentCloudSDKException
import com.tencentcloudapi.common.profile.ClientProfile
import com.tencentcloudapi.common.profile.HttpProfile
import groovy.util.logging.Slf4j

import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAccessor

@Slf4j
abstract class AbstractTencentServiceClient {
  final MAX_QUERY_TIME = 1000
  final DEFAULT_LIMIT = 100

  abstract String getEndPoint()
  Credential cred
  HttpProfile httpProfile
  ClientProfile clientProfile

  AbstractTencentServiceClient(String secretId, String secretKey) {
    cred = new Credential(secretId, secretKey)
    httpProfile = new HttpProfile()
    httpProfile.setEndpoint(endPoint)
    clientProfile = new ClientProfile()
    clientProfile.setHttpProfile(httpProfile)
  }

  def iterQuery(maxItemNum=0, closure) {
    List<AbstractModel> models = []
    def query_index = 0
    def offset = 0
    def limit = DEFAULT_LIMIT
    try {
      while (query_index++ < MAX_QUERY_TIME) {
        def result = closure(offset, limit) as List
        if (result) {
          if (maxItemNum && models.size() + result.size() > maxItemNum) {
            break
          }

          models.addAll result
          offset += limit
        } else {
          break
        }
        sleep(500)
      }
      models
    } catch (TencentCloudSDKException e) {
      throw new TencentOperationException(e.toString())
    }
  }


  static Date ConvertIsoDateTime(String isoDateTime) {
    try {
      DateTimeFormatter timeFormatter = DateTimeFormatter.ISO_DATE_TIME
      TemporalAccessor accessor = timeFormatter.parse(isoDateTime)
      Date date = Date.from(Instant.from(accessor))
      return date
    } catch (Exception e) {
      log.warn "convert time error ${e.toString()}"
      return null
    }
  }
}
