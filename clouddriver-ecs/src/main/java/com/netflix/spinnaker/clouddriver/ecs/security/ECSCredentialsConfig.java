package com.netflix.spinnaker.clouddriver.ecs.security;


import com.netflix.spinnaker.clouddriver.aws.security.config.CredentialsConfig;
import lombok.Data;

import java.util.List;


@Data
public class ECSCredentialsConfig {
  List<Account> accounts;

  @Data
  public static class Account {
    private String name;
    private String awsAccount;
  }
}
