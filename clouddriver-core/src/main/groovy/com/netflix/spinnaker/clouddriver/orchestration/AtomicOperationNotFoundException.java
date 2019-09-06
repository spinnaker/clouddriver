package com.netflix.spinnaker.clouddriver.orchestration;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(
    value = HttpStatus.BAD_REQUEST,
    reason = "Could not find a suitable converter for supplied type.")
public class AtomicOperationNotFoundException extends RuntimeException {
  public AtomicOperationNotFoundException() {}

  public AtomicOperationNotFoundException(String message) {}

  public AtomicOperationNotFoundException(String message, Throwable cause) {}

  public AtomicOperationNotFoundException(Throwable cause) {}

  protected AtomicOperationNotFoundException(
      String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {}
}
