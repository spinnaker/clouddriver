package com.netflix.spinnaker.clouddriver.orchestration;

import com.netflix.spinnaker.kork.exceptions.HasAdditionalAttributes;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class AtomicOperationException extends RuntimeException implements HasAdditionalAttributes {
  public AtomicOperationException(String message, List<String> errors) {
    super(message);
    this.errors = errors;
  }

  @Override
  public Map<String, Object> getAdditionalAttributes() {
    if (errors == null || errors.isEmpty()) {
      return new HashMap<>();
    }
    Map<String, Object> map = new HashMap<>();
    map.put("errors", errors);
    return map;
  }

  public List<String> getErrors() {
    return errors;
  }

  public void setErrors(List<String> errors) {
    this.errors = errors;
  }

  private List<String> errors;
}
