/*
 * Copyright 2019 Armory
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
 *
 */

package com.netflix.spinnaker.clouddriver.kubernetes.security;

import com.google.common.hash.Hashing;
import java.nio.file.Files;
import java.nio.file.Paths;
import javax.annotation.Nonnull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KubeconfigFileHasher {
  private static final Logger log = LoggerFactory.getLogger(KubeconfigFileHasher.class);

  public static String hashKubeconfigFile(@Nonnull String filepath) {
    if (filepath.isEmpty()) {
      return "";
    }
    try {
      byte[] contents = Files.readAllBytes(Paths.get(filepath));
      return Hashing.sha256().hashBytes(contents).toString();
    } catch (Exception e) {
      log.warn("failed to hash kubeconfig file at {}: {}", filepath, e);
      return "";
    }
  }
}
