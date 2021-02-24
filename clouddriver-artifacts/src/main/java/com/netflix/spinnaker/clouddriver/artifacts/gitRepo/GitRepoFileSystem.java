/*
 * Copyright 2021 Armory
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

package com.netflix.spinnaker.clouddriver.artifacts.gitRepo;

import com.google.common.hash.Hashing;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.springframework.scheduling.annotation.Scheduled;

@Slf4j
public class GitRepoFileSystem {
  private static final Path CLONES_HOME =
      Paths.get(System.getProperty("java.io.tmpdir"), "gitrepos");

  private final int cloneRetentionMin;

  public GitRepoFileSystem(int cloneRetentionMin) {
    this.cloneRetentionMin = cloneRetentionMin;
  }

  public Path getLocalClonePath(String repoUrl, String branch) {
    return Paths.get(CLONES_HOME.toString(), hashCoordinates(repoUrl, branch));
  }

  private String hashCoordinates(String repoUrl, String branch) {
    String coordinates =
        String.format(
            "%s-%s",
            Optional.ofNullable(repoUrl).orElse("unknownUrl"),
            Optional.ofNullable(branch).orElse("defaultBranch"));
    return Hashing.sha256().hashString(coordinates, Charset.defaultCharset()).toString();
  }

  @Scheduled(fixedDelay = 1000 * 60)
  private void deleteExpiredRepos() {
    try {
      if (!CLONES_HOME.toFile().exists() || cloneRetentionMin < 1) {
        return;
      }
      File[] repos = CLONES_HOME.toFile().listFiles();
      if (repos == null) {
        return;
      }
      for (File r : repos) {
        long ageMin = ((System.currentTimeMillis() - r.lastModified()) / 1000) / 60;
        if (ageMin > cloneRetentionMin) {
          log.info("Deleting expired git clone {}", r.getName());
          FileUtils.forceDelete(r);
        }
      }
    } catch (IOException e) {
      log.error("Error deleting expired git clones, ignoring", e);
    }
  }
}
