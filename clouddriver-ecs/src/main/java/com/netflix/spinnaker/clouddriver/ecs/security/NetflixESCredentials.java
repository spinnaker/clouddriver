package com.netflix.spinnaker.clouddriver.ecs.security;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials;

public class NetflixESCredentials extends NetflixAmazonCredentials {
  private static final String CLOUD_PROVIDER = "ecs";

  public NetflixESCredentials(NetflixAmazonCredentials copy) {
    super(copy, copy.getCredentialsProvider());
  }

  @Override
  public String getCloudProvider() {
    return CLOUD_PROVIDER;
  }

  @Override
  public String getAccountType() {
    return CLOUD_PROVIDER;
  }
}
