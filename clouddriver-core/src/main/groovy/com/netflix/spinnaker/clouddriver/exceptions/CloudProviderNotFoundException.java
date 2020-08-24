package com.netflix.spinnaker.clouddriver.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(value = HttpStatus.BAD_REQUEST)
public class CloudProviderNotFoundException extends RuntimeException {
  public CloudProviderNotFoundException() {}

  public CloudProviderNotFoundException(String message) {}

  public CloudProviderNotFoundException(String message, Throwable cause) {}

  public CloudProviderNotFoundException(Throwable cause) {}

  protected CloudProviderNotFoundException(
      String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {}
}
