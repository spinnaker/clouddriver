package com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v2;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class CreateServiceBindingTest {
  @Test
  void shouldReplaceInvalidNameCharacters() {
    String invalidBindingName = "test-service-binding~123#test";
    String sanitisedBindingName = "test-service-binding-123-test";

    CreateServiceBinding binding =
        new CreateServiceBinding(
            UUID.randomUUID().toString(), UUID.randomUUID().toString(), invalidBindingName);
    assertThat(binding.getName()).isEqualTo(sanitisedBindingName);
  }
}
