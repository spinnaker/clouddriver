/*
 * Copyright 2020 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.exceptions;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.netflix.spinnaker.kork.annotations.NonnullByDefault;
import com.netflix.spinnaker.kork.exceptions.SpinnakerException;
import java.util.Optional;
import lombok.Getter;
import retrofit.RetrofitError;

// todo(mneterval): move to kork-exceptions

/** An exception that exposes the message of a {@link RetrofitError}. */
@NonnullByDefault
public class SpinnakerServerException extends SpinnakerException {
  private final String rawMessage;

  /**
   * Parses the message from the {@link RetrofitErrorResponseBody} of a {@link RetrofitError}.
   *
   * @param e The {@link RetrofitError} thrown by an invocation of the {@link retrofit.RestAdapter}
   */
  public SpinnakerServerException(RetrofitError e) {
    super(e.getCause());
    RetrofitErrorResponseBody body =
        (RetrofitErrorResponseBody) e.getBodyAs(RetrofitErrorResponseBody.class);
    this.rawMessage =
        Optional.ofNullable(body).map(RetrofitErrorResponseBody::getMessage).orElse("");
  }

  @Override
  public String getMessage() {
    return rawMessage;
  }

  final String getRawMessage() {
    return rawMessage;
  }

  @Getter
  private static final class RetrofitErrorResponseBody {
    private final String message;

    @JsonCreator
    RetrofitErrorResponseBody(@JsonProperty("message") String message) {
      this.message = message;
    }
  }
}
