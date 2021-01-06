package com.netflix.spinnaker.clouddriver.aws.deploy.userdata

import spock.lang.Specification

class UserDataProviderAggregatorSpec extends Specification {

  UserDataProviderAggregator userDataProviderAggregator = new UserDataProviderAggregator([new UserDataProviderA(), new UserDataProviderB()])

  static final String APP = 'app'
  static final String STACK = 'stack'
  static final String COUNTRIES = 'countries'
  static final String DEV_PHASE = 'devPhase'
  static final String HARDWARE = 'hardware'
  static final String PARTNERS = 'partners'
  static final String REVISION = 99
  static final String ZONE = 'zone'
  static final String REGION = 'region'
  static final String ACCOUNT = 'account'
  static final String ENVIRONMENT = 'environment'
  static final String ACCOUNT_TYPE = 'accountType'
  static final String DETAIL = "detail-c0${COUNTRIES}-d0${DEV_PHASE}-h0${HARDWARE}-p0${PARTNERS}-r0${REVISION}-z0${ZONE}"
  static final String ASG_NAME = "${APP}-${STACK}-${DETAIL}"
  static final String LAUNCH_CONFIG_NAME = 'launchConfigName'

  void "User data is aggregated correctly; a -> b -> user supplied user data"() {
    given:
    UserDataProvider.UserDataRequest request = UserDataProvider.UserDataRequest
      .builder()
      .asgName(ASG_NAME)
      .launchSettingName(LAUNCH_CONFIG_NAME)
      .environment(ENVIRONMENT)
      .region(REGION)
      .account(ACCOUNT)
      .accountType(ACCOUNT_TYPE)
      .build()

    when:
    //export USERDATA=1
    String result = userDataProviderAggregator.aggregate("ZXhwb3J0IFVTRVJEQVRBPTEK", request)

    then:
    //a
    //b
    //export USERDATA=1
    result == "YQpiCmV4cG9ydCBVU0VSREFUQT0xCg=="
  }

  void "User data is overrode with the user supplied base64 encoded user data and tokens are replaced correctly "() {
    given:
    UserDataProvider.UserDataRequest request = UserDataProvider.UserDataRequest
      .builder()
      .asgName(ASG_NAME)
      .launchSettingName(LAUNCH_CONFIG_NAME)
      .environment(ENVIRONMENT)
      .region(REGION)
      .account(ACCOUNT)
      .accountType(ACCOUNT_TYPE)
      .overrideDefaultUserData(true)
      .build()

    when:
    //NETFLIX_ACCOUNT="%%account%%"
    //NETFLIX_ACCOUNT_TYPE="%%accounttype%%"
    //NETFLIX_ENVIRONMENT="%%env%%"
    //NETFLIX_APP="%%app%%"
    //NETFLIX_APPUSER="%%app%%"
    //NETFLIX_STACK="%%stack%%"
    //NETFLIX_CLUSTER="%%cluster%%"
    //NETFLIX_DETAIL="%%detail%%"
    //NETFLIX_AUTO_SCALE_GROUP="%%autogrp%%"
    //NETFLIX_LAUNCH_CONFIG="%%launchconfig%%"
    //NETFLIX_LAUNCH_TEMPLATE="%%launchtemplate%%"
    //EC2_REGION="%%region%%"
    String result = userDataProviderAggregator.aggregate("TkVURkxJWF9BQ0NPVU5UPSIlJWFjY291bnQlJSIKTkVURkxJWF9BQ0NPVU5UX1RZUEU9IiUlYWNjb3VudHR5cGUlJSIKTkVURkxJWF9FTlZJUk9OTUVOVD0iJSVlbnYlJSIKTkVURkxJWF9BUFA9IiUlYXBwJSUiCk5FVEZMSVhfQVBQVVNFUj0iJSVhcHAlJSIKTkVURkxJWF9TVEFDSz0iJSVzdGFjayUlIgpORVRGTElYX0NMVVNURVI9IiUlY2x1c3RlciUlIgpORVRGTElYX0RFVEFJTD0iJSVkZXRhaWwlJSIKTkVURkxJWF9BVVRPX1NDQUxFX0dST1VQPSIlJWF1dG9ncnAlJSIKTkVURkxJWF9MQVVOQ0hfQ09ORklHPSIlJWxhdW5jaGNvbmZpZyUlIgpORVRGTElYX0xBVU5DSF9URU1QTEFURT0iJSVsYXVuY2h0ZW1wbGF0ZSUlIgpFQzJfUkVHSU9OPSIlJXJlZ2lvbiUlIg==", request)

    then:
    //NETFLIX_ACCOUNT="account"
    //NETFLIX_ACCOUNT_TYPE="accountType"
    //NETFLIX_ENVIRONMENT="environment"
    //NETFLIX_APP="app"
    //NETFLIX_APPUSER="app"
    //NETFLIX_STACK="stack"
    //NETFLIX_CLUSTER="app-stack-detail-c0countries-d0devPhase-h0hardware-p0partners-r099-z0zone"
    //NETFLIX_DETAIL="detail-c0countries-d0devPhase-h0hardware-p0partners-r099-z0zone"
    //NETFLIX_AUTO_SCALE_GROUP="app-stack-detail-c0countries-d0devPhase-h0hardware-p0partners-r099-z0zone"
    //NETFLIX_LAUNCH_CONFIG="launchConfigName"
    //NETFLIX_LAUNCH_TEMPLATE=""
    //EC2_REGION="region"
    result == "TkVURkxJWF9BQ0NPVU5UPSJhY2NvdW50IgpORVRGTElYX0FDQ09VTlRfVFlQRT0iYWNjb3VudFR5cGUiCk5FVEZMSVhfRU5WSVJPTk1FTlQ9ImVudmlyb25tZW50IgpORVRGTElYX0FQUD0iYXBwIgpORVRGTElYX0FQUFVTRVI9ImFwcCIKTkVURkxJWF9TVEFDSz0ic3RhY2siCk5FVEZMSVhfQ0xVU1RFUj0iYXBwLXN0YWNrLWRldGFpbC1jMGNvdW50cmllcy1kMGRldlBoYXNlLWgwaGFyZHdhcmUtcDBwYXJ0bmVycy1yMDk5LXowem9uZSIKTkVURkxJWF9ERVRBSUw9ImRldGFpbC1jMGNvdW50cmllcy1kMGRldlBoYXNlLWgwaGFyZHdhcmUtcDBwYXJ0bmVycy1yMDk5LXowem9uZSIKTkVURkxJWF9BVVRPX1NDQUxFX0dST1VQPSJhcHAtc3RhY2stZGV0YWlsLWMwY291bnRyaWVzLWQwZGV2UGhhc2UtaDBoYXJkd2FyZS1wMHBhcnRuZXJzLXIwOTktejB6b25lIgpORVRGTElYX0xBVU5DSF9DT05GSUc9ImxhdW5jaENvbmZpZ05hbWUiCk5FVEZMSVhfTEFVTkNIX1RFTVBMQVRFPSIiCkVDMl9SRUdJT049InJlZ2lvbiI="
  }
}

class UserDataProviderA implements UserDataProvider {
  String getUserData(UserDataRequest userDataRequest) {
    return "a"
  }
}

class UserDataProviderB implements UserDataProvider {
  String getUserData(UserDataRequest userDataRequest) {
    return "b"
  }
}
