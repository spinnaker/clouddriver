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

import com.netflix.spinnaker.clouddriver.artifacts.config.ArtifactCredentials;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.jgit.api.ArchiveCommand;
import org.eclipse.jgit.api.CheckoutCommand;
import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.archive.ArchiveFormats;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;

@Slf4j
public class GitRepoArtifactCredentials implements ArtifactCredentials {
  @Getter private final List<String> types = Collections.singletonList("git/repo");

  @Getter private final String name;
  private final String username;
  private final String password;

  public GitRepoArtifactCredentials(GitRepoArtifactAccount account) {
    this.name = account.getName();
    this.username = account.getUsername();
    this.password = account.getPassword();
  }

  @Override
  public InputStream download(Artifact artifact) throws IOException {
    String repoReference = artifact.getReference();
    Path stagingPath =
        Paths.get(System.getProperty("java.io.tmpdir"), UUID.randomUUID().toString());

    try {
      log.info("Cloning git/repo {} into {}", repoReference, stagingPath.toString());
      Git localRepository = clone(artifact, stagingPath);
      ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
      log.info("Creating archive for git/repo {}", repoReference);
      archiveToOutputStream(artifact, localRepository, outputStream);
      return new ByteArrayInputStream(outputStream.toByteArray());
    } catch (Exception e) {
      throw new IOException("Failed to clone git/repo " + repoReference, e);
    } finally {
      log.info("Cleaning up git/repo {}", repoReference);
      try {
        FileUtils.deleteDirectory(stagingPath.toFile());
      } catch (IOException e) {
        log.info("Failed to cleanup git/repo for {}", repoReference);
        throw new RuntimeException(
            "Failed to clean up git/repo staging directory for repo" + repoReference, e);
      }
    }
  }

  private Git clone(Artifact artifact, Path stagingPath) throws Exception {
    String version = artifact.getVersion() != "" ? artifact.getVersion() : "master";
    String subPath = artifactSubPath(artifact);
    // TODO(ethanfrogers): add support for clone history depth once jgit supports it

    Git localRepository =
        addAuthentication(Git.cloneRepository())
            .setURI(artifact.getReference())
            .setDirectory(stagingPath.toFile())
            .setNoCheckout(true)
            .call();

    CheckoutCommand checkoutCommand =
        localRepository.checkout().setName(version).setStartPoint("origin/" + version);

    if (!StringUtils.isEmpty(subPath)) {
      checkoutCommand = checkoutCommand.addPath(subPath);
    }

    checkoutCommand.call();
    return localRepository;
  }

  private void archiveToOutputStream(Artifact artifact, Git repository, OutputStream outputStream)
      throws Exception {
    String version = artifact.getVersion() != "" ? artifact.getVersion() : "master";
    String subPath = artifactSubPath(artifact);

    try {
      ArchiveFormats.registerAll();
      ArchiveCommand archiveCommand =
          repository
              .archive()
              .setTree(repository.getRepository().resolve("origin/" + version))
              .setFormat("tgz")
              .setOutputStream(outputStream);

      if (!StringUtils.isEmpty(subPath)) {
        archiveCommand.setPaths(subPath);
      }

      archiveCommand.call();
    } finally {
      ArchiveFormats.unregisterAll();
    }
  }

  private String artifactSubPath(Artifact artifact) {
    String target = "";
    Map<String, Object> metadata = artifact.getMetadata();
    if (metadata != null) {
      target = (String) metadata.getOrDefault("subPath", "");
    }

    return target;
  }

  private CloneCommand addAuthentication(CloneCommand cloneCommand) {
    // TODO(ethanfrogers): support github oauth token and ssh authentication
    if (!StringUtils.isEmpty(username) && !StringUtils.isEmpty(password)) {
      return cloneCommand.setCredentialsProvider(
          new UsernamePasswordCredentialsProvider(username, password));
    }

    return cloneCommand;
  }
}
