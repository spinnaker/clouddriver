package com.netflix.spinnaker.clouddriver.deploy;

public class DeployHandlerNotFoundException extends RuntimeException {
  public DeployHandlerNotFoundException() {}

  public DeployHandlerNotFoundException(String message) {}

  public DeployHandlerNotFoundException(String message, Throwable cause) {}

  public DeployHandlerNotFoundException(Throwable cause) {}

  protected DeployHandlerNotFoundException(
      String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {}
}
