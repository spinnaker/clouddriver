package com.netflix.spinnaker.clouddriver.tencent.client;

import static java.lang.Thread.sleep;

import com.netflix.spinnaker.clouddriver.tencent.exception.TencentOperationException;
import com.tencentcloudapi.common.AbstractModel;
import com.tencentcloudapi.common.Credential;
import com.tencentcloudapi.common.exception.TencentCloudSDKException;
import com.tencentcloudapi.common.profile.ClientProfile;
import com.tencentcloudapi.common.profile.HttpProfile;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Data
public abstract class AbstractTencentServiceClient {
  public abstract String getEndPoint();

  public AbstractTencentServiceClient(String secretId, String secretKey) {
    cred = new Credential(secretId, secretKey);
    httpProfile = new HttpProfile();
    httpProfile.setEndpoint(getEndPoint());
    clientProfile = new ClientProfile();
    clientProfile.setHttpProfile(httpProfile);
  }

  public <T extends AbstractModel> List<T> iterQuery(Integer maxItemNum, IterQuery iterator) {
    List<T> models = new ArrayList<T>();
    Integer query_index = 0;
    Integer offset = 0;
    Integer limit = DEFAULT_LIMIT;
    try {
      while (query_index++ < MAX_QUERY_TIME) {
        List<T> result = iterator.iter(offset, limit);
        if (result != null && result.size() > 0) {
          models.addAll(result);
          if (maxItemNum > 0 && models.size() >= maxItemNum) {
            break;
          }
          offset += limit;
        } else {
          break;
        }
        sleep(500);
      }

      return models;
    } catch (TencentCloudSDKException | InterruptedException e) {
      throw new TencentOperationException(e.toString());
    }
  }

  public <T extends AbstractModel> List<T> iterQuery(IterQuery iterator) {
    return iterQuery(0, iterator);
  }

  public static Date ConvertIsoDateTime(String isoDateTime) {
    try {
      DateTimeFormatter timeFormatter = DateTimeFormatter.ISO_DATE_TIME;
      TemporalAccessor accessor = timeFormatter.parse(isoDateTime);
      Date date = Date.from(Instant.from(accessor));
      return date;
    } catch (Exception e) {
      // log.warn("convert time error " + e.toString());
      return null;
    }
  }

  protected final Integer MAX_QUERY_TIME = 1000;
  protected final Integer DEFAULT_LIMIT = 100;
  private Credential cred;
  private HttpProfile httpProfile;
  private ClientProfile clientProfile;
}
