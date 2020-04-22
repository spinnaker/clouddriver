/*
 * Copyright 2020 Google, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.googlecommon.deploy;

import com.google.api.client.googleapis.json.GoogleJsonError;
import com.google.api.client.googleapis.json.GoogleJsonError.ErrorInfo;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.common.collect.ImmutableList;
import java.util.Optional;

public class GoogleApiException extends RuntimeException {
  GoogleApiException(String message) {
    super(message);
  }

  static GoogleApiException fromGoogleJsonException(GoogleJsonResponseException e) {
    Optional<ErrorInfo> optionalErrorInfo =
        Optional.ofNullable(e.getDetails()).map(GoogleJsonError::getErrors)
            .orElse(ImmutableList.of()).stream()
            .findFirst();

    if (optionalErrorInfo.isPresent()) {
      ErrorInfo errorInfo = optionalErrorInfo.get();
      String message =
          String.format(
              "Operation failed. Last attempt returned status code %s with error message %s and reason %s.",
              e.getStatusCode(), errorInfo.getMessage(), errorInfo.getReason());
      if ("resourceInUseByAnotherResource".equals(errorInfo.getReason())) {
        return new ResourceInUseException(message);
      } else {
        return new GoogleApiException(message);
      }
    } else {
      return new GoogleApiException(
          String.format(
              "Operation failed. Last attempt returned status code %s with message %s.",
              e.getStatusCode(), e.getMessage()));
    }
  }

  public static class ResourceInUseException extends GoogleApiException {
    ResourceInUseException(String message) {
      super(message);
    }
  }
}
