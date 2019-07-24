package com.netflix.spinnaker.clouddriver.titus.caching.utils;

import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider;
import com.netflix.spinnaker.clouddriver.titus.credentials.NetflixTitusCredentials;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import javax.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class CachingSchemaUtil {

  private final AccountCredentialsProvider accountCredentialsProvider;
  private final AwsLookupUtil awsLookupUtil;

  private Map<String, CachingSchema> cachingSchemaForAccounts = new LinkedHashMap<>();

  @Autowired
  public CachingSchemaUtil(
      AccountCredentialsProvider accountCredentialsProvider, AwsLookupUtil awsLookupUtil) {
    this.accountCredentialsProvider = accountCredentialsProvider;
    this.awsLookupUtil = awsLookupUtil;
  }

  private static CachingSchema cachingSchemaFor(NetflixTitusCredentials credentials) {
    return credentials.getSplitCachingEnabled() ? CachingSchema.V2 : CachingSchema.V1;
  }

  public CachingSchema getCachingSchemaForAccount(String account) {
    return Optional.ofNullable(cachingSchemaForAccounts.get(account)).orElse(CachingSchema.V1);
  }

  @PostConstruct
  private void init() {
    accountCredentialsProvider.getAll().stream()
        .filter(c -> c instanceof NetflixTitusCredentials)
        .forEach(
            c -> {
              NetflixTitusCredentials credentials = (NetflixTitusCredentials) c;
              credentials
                  .getRegions()
                  .forEach(
                      region -> {
                        cachingSchemaForAccounts.put(
                            credentials.getName(), cachingSchemaFor(credentials));
                        cachingSchemaForAccounts.put(
                            awsLookupUtil.awsAccountId(credentials.getName(), region.getName()),
                            cachingSchemaFor(credentials));
                      });
            });
  }
}
