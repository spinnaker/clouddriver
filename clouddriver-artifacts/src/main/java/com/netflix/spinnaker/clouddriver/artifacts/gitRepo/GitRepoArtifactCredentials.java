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
 */

package com.netflix.spinnaker.clouddriver.artifacts.gitRepo;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.netflix.spinnaker.clouddriver.artifacts.config.ArtifactCredentials;
import com.netflix.spinnaker.kork.annotations.NonnullByDefault;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@NonnullByDefault
@Slf4j
final class GitRepoArtifactCredentials implements ArtifactCredentials {

  private static final Pattern GIT_URL_PATTERN = Pattern.compile("^.*/(.*)\\.git$");

  @Getter private final ImmutableList<String> types = ImmutableList.of("git/repo");
  @Getter private final String name;

  private final GitJobExecutor executor;

  public GitRepoArtifactCredentials(GitJobExecutor executor) {
    this.executor = executor;
    this.name = this.executor.getAccount().getName();
  }

  @Override
  public InputStream download(Artifact artifact) throws IOException {
    String repoReference = artifact.getReference();
    String subPath = artifactSubPath(artifact);
    String remoteRef = artifactVersion(artifact);
    Path stagingPath =
        Paths.get(System.getProperty("java.io.tmpdir"), UUID.randomUUID().toString());

    executor.clone(repoReference, remoteRef, stagingPath);
    String repoBasename = getRepoBasename(repoReference);
    Path outputFile = Paths.get(stagingPath.toString(), repoBasename + ".tgz");

    log.info("Creating archive for git/repo {}", repoReference);

    executor.archive(
        Paths.get(stagingPath.toString(), repoBasename), remoteRef, subPath, outputFile);

    return new FileInputStream(outputFile.toFile());
  }

  private String getRepoBasename(String url) {
    Matcher matcher = GIT_URL_PATTERN.matcher(url);
    if (!matcher.matches()) {
      throw new IllegalArgumentException(
          "Git repo url " + url + " doesn't match regex " + GIT_URL_PATTERN);
    }
    return matcher.group(1);
  }

  private String artifactSubPath(Artifact artifact) {
    if (!Strings.nullToEmpty(artifact.getLocation()).isEmpty()) {
      return artifact.getLocation();
    }
    return Strings.nullToEmpty((String) artifact.getMetadata("subPath"));
  }

  private String artifactVersion(Artifact artifact) {
    return !Strings.isNullOrEmpty(artifact.getVersion()) ? artifact.getVersion() : "master";
  }
}
