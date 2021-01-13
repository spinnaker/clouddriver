package com.netflix.spinnaker.clouddriver.aws.userdata;

import javax.annotation.Nonnull;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class UserDataOverride {
  private boolean enabled;

  @Nonnull private String tokenizerName = "default";
}
