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

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.netflix.spinnaker.clouddriver.artifacts.config.ArtifactCredentials;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
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
import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.archive.TgzFormat;
import org.eclipse.jgit.transport.JschConfigSessionFactory;
import org.eclipse.jgit.transport.OpenSshConfig;
import org.eclipse.jgit.transport.SshSessionFactory;
import org.eclipse.jgit.transport.SshTransport;
import org.eclipse.jgit.transport.Transport;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.eclipse.jgit.util.FS;

@Slf4j
public class GitRepoArtifactCredentials implements ArtifactCredentials {
  @Getter private final List<String> types = Collections.singletonList("git/repo");

  @Getter private final String name;
  private final String username;
  private final String password;
  private final String token;
  private final String sshPrivateKeyFilePath;
  private final String sshPrivateKeyPassphrase;
  private final String sshKnownHostsFilePath;
  private final boolean sshTrustUnknownHosts;
  private final AuthType authType;

  private enum AuthType {
    HTTP,
    TOKEN,
    SSH,
    NONE
  }

  public GitRepoArtifactCredentials(GitRepoArtifactAccount account) {
    this.name = account.getName();
    this.username = account.getUsername();
    this.password = account.getPassword();
    this.token = account.getToken();
    this.sshPrivateKeyFilePath = account.getSshPrivateKeyFilePath();
    this.sshPrivateKeyPassphrase = account.getSshPrivateKeyPassphrase();
    this.sshKnownHostsFilePath = account.getSshKnownHostsFilePath();
    this.sshTrustUnknownHosts = account.isSshTrustUnknownHosts();

    if (!StringUtils.isEmpty(username) && !StringUtils.isEmpty(password)) {
      authType = AuthType.HTTP;
    } else if (!StringUtils.isEmpty(token)) {
      authType = AuthType.TOKEN;
    } else if (!StringUtils.isEmpty(sshPrivateKeyFilePath)) {
      authType = AuthType.SSH;
    } else {
      authType = AuthType.NONE;
    }

    ArchiveCommand.registerFormat("tgz", new TgzFormat());
  }

  @Override
  public InputStream download(Artifact artifact) throws IOException {
    String repoReference = artifact.getReference();
    String subPath = artifactSubPath(artifact);
    String remoteRef = artifactVersion(artifact);
    Path stagingPath =
        Paths.get(System.getProperty("java.io.tmpdir"), UUID.randomUUID().toString());

    if (!isValidReference(repoReference)) {
      throw new IOException(
          "Artifact reference "
              + repoReference
              + " is invalid for artifact account with auth type "
              + authType);
    }

    try (Closeable ignored = () -> FileUtils.deleteDirectory(stagingPath.toFile())) {
      log.info("Cloning git/repo {} into {}", repoReference, stagingPath.toString());
      Git localRepository = clone(artifact, stagingPath, remoteRef);
      ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
      log.info("Creating archive for git/repo {}", repoReference);
      archiveToOutputStream(localRepository, outputStream, remoteRef, subPath);
      return new ByteArrayInputStream(outputStream.toByteArray());
    } catch (GitAPIException e) {
      throw new IOException(
          "Failed to clone or archive git/repo " + repoReference + ": " + e.getMessage());
    }
  }

  private Git clone(Artifact artifact, Path stagingPath, String remoteRef) throws GitAPIException {
    // TODO(ethanfrogers): add support for clone history depth once jgit supports it

    return addAuthentication(Git.cloneRepository())
        .setURI(artifact.getReference())
        .setDirectory(stagingPath.toFile())
        .setBranch(remoteRef)
        .call();
  }

  private void archiveToOutputStream(
      Git repository, OutputStream outputStream, String remoteRef, String subPath)
      throws GitAPIException, IOException {

    ArchiveCommand archiveCommand =
        repository
            .archive()
            .setTree(repository.getRepository().resolve(remoteRef))
            .setFormat("tgz")
            .setOutputStream(outputStream);

    if (!StringUtils.isEmpty(subPath)) {
      archiveCommand.setPaths(subPath);
    }

    archiveCommand.call();
  }

  private String artifactSubPath(Artifact artifact) {
    String target = "";
    Map<String, Object> metadata = artifact.getMetadata();
    if (metadata != null) {
      target = (String) metadata.getOrDefault("subPath", "");
    }

    return target;
  }

  private String artifactVersion(Artifact artifact) {
    return !StringUtils.isEmpty(artifact.getVersion()) ? artifact.getVersion() : "master";
  }

  private CloneCommand addAuthentication(CloneCommand cloneCommand) {
    switch (authType) {
      case HTTP:
        return cloneCommand.setCredentialsProvider(
            new UsernamePasswordCredentialsProvider(username, password));
      case TOKEN:
        return cloneCommand.setCredentialsProvider(
            new UsernamePasswordCredentialsProvider(token, ""));
      case SSH:
        return configureSshAuth(cloneCommand);
      default:
        return cloneCommand;
    }
  }

  private CloneCommand configureSshAuth(CloneCommand cloneCommand) {
    SshSessionFactory sshSessionFactory =
        new JschConfigSessionFactory() {
          @Override
          protected void configure(OpenSshConfig.Host hc, Session session) {
            if (sshKnownHostsFilePath == null && sshTrustUnknownHosts) {
              session.setConfig("StrictHostKeyChecking", "no");
            }
          }

          @Override
          protected JSch createDefaultJSch(FS fs) throws JSchException {
            JSch defaultJSch = super.createDefaultJSch(fs);
            if (!StringUtils.isEmpty(sshPrivateKeyPassphrase)) {
              defaultJSch.addIdentity(sshPrivateKeyFilePath, sshPrivateKeyPassphrase);
            } else {
              defaultJSch.addIdentity(sshPrivateKeyFilePath);
            }

            if (sshKnownHostsFilePath != null && sshTrustUnknownHosts) {
              log.warn(
                  "SSH known_hosts file path supplied, ignoring 'sshTrustUnknownHosts' option");
            }

            if (sshKnownHostsFilePath != null) {
              defaultJSch.setKnownHosts(sshKnownHostsFilePath);
            }

            return defaultJSch;
          }
        };

    return cloneCommand.setTransportConfigCallback(
        (Transport transport) -> {
          SshTransport sshTransport = (SshTransport) transport;
          sshTransport.setSshSessionFactory(sshSessionFactory);
        });
  }

  private boolean isValidReference(String reference) {
    if (authType == AuthType.HTTP || authType == AuthType.TOKEN) {
      return reference.startsWith("http");
    }
    if (authType == AuthType.SSH) {
      return reference.startsWith("ssh://") || reference.startsWith("git@");
    }
    return true;
  }
}
