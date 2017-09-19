package com.netflix.spinnaker.clouddriver.ecs.security;


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
