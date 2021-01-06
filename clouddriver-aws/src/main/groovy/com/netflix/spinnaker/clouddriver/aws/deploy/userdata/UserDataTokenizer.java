package com.netflix.spinnaker.clouddriver.aws.deploy.userdata;

import com.netflix.frigga.Names;

/** Utility class to replace tokens in user data templates. */
public class UserDataTokenizer {

  /**
   * Replaces the tokens that are present in the supplied user data.
   *
   * @param names {@link Names}
   * @param userDataRequest {@link UserDataProvider.UserDataRequest}
   * @param rawUserData The user data to replace tokens in
   * @param legacyUdf
   * @return String
   */
  static String replaceTokens(
      Names names,
      UserDataProvider.UserDataRequest userDataRequest,
      String rawUserData,
      Boolean legacyUdf) {
    String stack = isPresent(names.getStack()) ? names.getStack() : "";
    String cluster = isPresent(names.getCluster()) ? names.getCluster() : "";
    String revision = isPresent(names.getRevision()) ? names.getRevision() : "";
    String countries = isPresent(names.getCountries()) ? names.getCountries() : "";
    String devPhase = isPresent(names.getDevPhase()) ? names.getDevPhase() : "";
    String hardware = isPresent(names.getHardware()) ? names.getHardware() : "";
    String zone = isPresent(names.getZone()) ? names.getZone() : "";
    String detail = isPresent(names.getDetail()) ? names.getDetail() : "";

    // Replace the tokens & return the result
    String result =
        rawUserData
            .replace("%%account%%", userDataRequest.getAccount())
            .replace("%%accounttype%%", userDataRequest.getAccountType())
            .replace(
                "%%env%%",
                (legacyUdf != null && legacyUdf)
                    ? userDataRequest.getAccount()
                    : userDataRequest.getEnvironment())
            .replace("%%app%%", names.getApp())
            .replace("%%region%%", userDataRequest.getRegion())
            .replace("%%group%%", names.getGroup())
            .replace("%%autogrp%%", names.getGroup())
            .replace("%%revision%%", revision)
            .replace("%%countries%%", countries)
            .replace("%%devPhase%%", devPhase)
            .replace("%%hardware%%", hardware)
            .replace("%%zone%%", zone)
            .replace("%%cluster%%", cluster)
            .replace("%%stack%%", stack)
            .replace("%%detail%%", detail)
            .replace("%%tier%%", "");

    if (userDataRequest.getLaunchTemplate() != null && userDataRequest.getLaunchTemplate()) {
      result =
          result
              .replace("%%launchtemplate%%", userDataRequest.getLaunchSettingName())
              .replace("%%launchconfig%%", "");
    } else {
      result =
          result
              .replace("%%launchconfig%%", userDataRequest.getLaunchSettingName())
              .replace("%%launchtemplate%%", "");
    }

    return result;
  }

  private static boolean isPresent(String value) {
    return value != null && !value.isBlank();
  }
}
