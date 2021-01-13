package com.netflix.spinnaker.clouddriver.aws.userdata;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.annotation.Nonnull;

@Data
@NoArgsConstructor
public class UserDataOverride {
  private boolean enabled;

  @Nonnull
  private String tokenizerName = "default";
}
