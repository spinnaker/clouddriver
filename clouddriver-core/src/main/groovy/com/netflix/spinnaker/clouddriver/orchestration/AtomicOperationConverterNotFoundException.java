package com.netflix.spinnaker.clouddriver.orchestration;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(value = HttpStatus.BAD_REQUEST)
public class AtomicOperationConverterNotFoundException extends RuntimeException {
  public AtomicOperationConverterNotFoundException() {}

  public AtomicOperationConverterNotFoundException(String message) {}

  public AtomicOperationConverterNotFoundException(String message, Throwable cause) {}

  public AtomicOperationConverterNotFoundException(Throwable cause) {}

  protected AtomicOperationConverterNotFoundException(
      String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {}
}
