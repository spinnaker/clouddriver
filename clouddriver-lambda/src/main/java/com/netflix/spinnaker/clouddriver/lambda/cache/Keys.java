/*
 * Copyright 2018 Amazon.com, Inc. or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.clouddriver.lambda.cache;

import com.google.common.base.CaseFormat;
import com.netflix.spinnaker.clouddriver.cache.KeyParser;

import java.util.HashMap;
import java.util.Map;

import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.HEALTH;
import static com.netflix.spinnaker.clouddriver.lambda.LambdaCloudProvider.ID;

public class Keys implements KeyParser {
  public enum Namespace {
    IAM_ROLE,
    LAMBDA_NAME;

    public final String ns;

    Namespace() {
      ns = CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.LOWER_CAMEL, this.name());
    }

    public String toString() {
      return ns;
    }
  }

  public static final String SEPARATOR = ":";

  @Override
  public String getCloudProvider() {
    return ID;
  }

  @Override
  public Map<String, String> parseKey(String key) {
    return parse(key);
  }

  @Override
  public Boolean canParseType(String type) {
    return canParse(type);
  }

  private static Boolean canParse(String type) {
    for (Namespace key : Namespace.values()) {
      if (key.toString().equals(type)) {
        return true;
      }
    }
    return false;
  }

  public static Map<String, String> parse(String key) {
    String[] parts = key.split(SEPARATOR);

    if (parts.length < 3 || !parts[0].equals(ID)) {
      return null;
    }

    Map<String, String> result = new HashMap<>();
    result.put("provider", parts[0]);
    result.put("type", parts[1]);
    result.put("account", parts[2]);

    if(!canParse(parts[1]) && parts[1].equals(HEALTH.getNs())){
      result.put("region", parts[3]);
      result.put("taskId", parts[4]);
      return result;
    }


    Namespace namespace = Namespace.valueOf(CaseFormat.LOWER_CAMEL.to(CaseFormat.UPPER_UNDERSCORE, parts[1]));

    if (!namespace.equals(Namespace.IAM_ROLE)) {
      result.put("region", parts[3]);
    }

    switch (namespace) {
      case LAMBDA_NAME:
        result.put("AwsLambdaName", parts[4]);
        break;
      case IAM_ROLE:
        result.put("roleName", parts[3]);
        break;
      default:
        break;
    }

    return result;
  }

  @Override
  public Boolean canParseField(String type) {
    return false;
  }

  public static String getLambdaFunctionKey(String account, String region, String awsLambdaFunctionName) {
    return buildKey("arn:aws:lambda", account, region, "function",awsLambdaFunctionName);
  }


  public static String getIamRoleKey(String account, String iamRoleName) {
    return ID + SEPARATOR + Namespace.IAM_ROLE + SEPARATOR + account + SEPARATOR + iamRoleName;
  }

  private static String buildKey(String namespace,String account, String region, String type, String identifier){
    return namespace + SEPARATOR + region + SEPARATOR + account + SEPARATOR  + type + SEPARATOR + identifier;
  }
}
