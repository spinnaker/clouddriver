package com.netflix.spinnaker.clouddriver.tencent.exception;

public class TencentOperationException extends RuntimeException {
  public TencentOperationException() {
  }

  public TencentOperationException(String message) {
    super(message);
  }

  public TencentOperationException(String message, Throwable cause) {
    super(message, cause);
  }

  public TencentOperationException(Throwable cause) {
    super(cause);
  }

  protected TencentOperationException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
    super(message, cause, enableSuppression, writableStackTrace);
  }
}
