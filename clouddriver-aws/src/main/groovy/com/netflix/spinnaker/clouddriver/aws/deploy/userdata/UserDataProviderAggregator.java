package com.netflix.spinnaker.clouddriver.aws.deploy.userdata;

import com.netflix.frigga.Names;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Aggregates all user data from the configured list of providers (see {@link UserDataProvider}).
 */
public class UserDataProviderAggregator {

  private final List<UserDataProvider> providers;

  public UserDataProviderAggregator(List<UserDataProvider> providers) {
    this.providers = providers;
  }

  /**
   * Aggregates all user data. First iterates through all providers and joins user data with a
   * newline. Then, adds the user supplied base64 encoded user data and again joins with a newline.
   * The result is such that the user supplied base64 encoded user data is always appended last to
   * the user data.
   *
   * <p>Note, if the {@link UserDataProvider.UserDataRequest} parameter overrideDefaultUserData is
   * true, then the user data from the providers is skipped and the user supplied base64 encoded
   * user data is used as the override. If this is the case, the standard set of user data format
   * tokens ("%%app%%, for example) are replaced in the user data - effectively processing the user
   * data as a UDF template.
   *
   * @param base64UserData String
   * @param userDataRequest {@link UserDataProvider.UserDataRequest}
   * @return String
   */
  public String aggregate(String base64UserData, UserDataProvider.UserDataRequest userDataRequest) {
    List<String> allUserData = new ArrayList<>();
    if (providers != null && (!userDataRequest.isOverrideDefaultUserData())) {
      allUserData =
          providers.stream().map(p -> p.getUserData(userDataRequest)).collect(Collectors.toList());
    }
    String data = String.join("\n", allUserData);

    byte[] bytes = Base64.getDecoder().decode(Optional.ofNullable(base64UserData).orElse(""));
    String userDataDecoded = new String(bytes, StandardCharsets.UTF_8);

    if (userDataRequest.isOverrideDefaultUserData()) {
      userDataDecoded =
          UserDataTokenizer.replaceTokens(
              Names.parseName(userDataRequest.getAsgName()),
              userDataRequest,
              userDataDecoded,
              false);
      return result(Collections.singletonList(userDataDecoded));
    }

    return result(Arrays.asList(data, userDataDecoded));
  }

  private String result(List<String> parts) {
    String result = String.join("\n", parts);
    if (result.startsWith("\n")) {
      result = result.trim();
    }

    if (result.isEmpty()) {
      return null;
    }

    return Base64.getEncoder().encodeToString(result.getBytes(StandardCharsets.UTF_8));
  }
}
