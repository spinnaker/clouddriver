package com.netflix.spinnaker.clouddriver.exceptions;

public class OperationTimedOutException extends RuntimeException {
  public OperationTimedOutException() {}

  public OperationTimedOutException(String message) {}

  public OperationTimedOutException(String message, Throwable cause) {}

  public OperationTimedOutException(Throwable cause) {}

  protected OperationTimedOutException(
      String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {}
}
