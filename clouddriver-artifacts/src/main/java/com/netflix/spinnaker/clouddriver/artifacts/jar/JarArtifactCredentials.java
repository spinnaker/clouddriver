/*
 * Copyright 2018 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.clouddriver.artifacts.jar;

import com.netflix.spinnaker.clouddriver.artifacts.config.ArtifactCredentials;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory;
import org.eclipse.aether.impl.DefaultServiceLocator;
import org.eclipse.aether.internal.impl.DefaultRepositoryLayoutProvider;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory;
import org.eclipse.aether.spi.connector.layout.RepositoryLayoutProvider;
import org.eclipse.aether.spi.connector.transport.TransporterFactory;
import org.eclipse.aether.transport.file.FileTransporterFactory;
import org.eclipse.aether.transport.http.HttpTransporterFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Slf4j
@Data
public class JarArtifactCredentials implements ArtifactCredentials {
  private final String name;
  private final List<String> types = Collections.singletonList("jar/archive");

  private final RepositorySystem repositorySystem;
  private final RepositorySystemSession repositorySystemSession;
  private final RemoteRepository remoteRepository;

  public JarArtifactCredentials(JarArtifactAccount account) {
    this.name = account.getName();
    this.repositorySystem = getRepositorySystem();
    this.repositorySystemSession = getRepositorySystemSession(this.repositorySystem);
    this.remoteRepository = new RemoteRepository.Builder(account.getName(), "default", account.getRepositoryUrl()).build();
  }

  public InputStream download(Artifact artifact) {
    File jarFile = getJarFile(artifact.getReference());
    try {
      return new FileInputStream(jarFile);
    } catch (FileNotFoundException e) {
      throw new IllegalStateException("File not found", e);
    }
  }

  private RepositorySystem getRepositorySystem() {
    DefaultServiceLocator locator = MavenRepositorySystemUtils.newServiceLocator();
    locator.addService(RepositoryConnectorFactory.class, BasicRepositoryConnectorFactory.class);
    locator.addService(TransporterFactory.class, FileTransporterFactory.class);
    locator.addService(TransporterFactory.class, HttpTransporterFactory.class);
    locator.addService(RepositoryLayoutProvider.class, DefaultRepositoryLayoutProvider.class);
    return locator.getService(RepositorySystem.class);
  }

  private Path getRepositoryPath() throws IOException {
    String uuid = UUID.randomUUID().toString();
    Path tempDir = Paths.get(System.getProperty("java.io.tmpdir"), uuid);
    if (!Files.isDirectory(tempDir)) {
      Files.createDirectories(tempDir);
    }
    return tempDir;
  }

  private RepositorySystemSession getRepositorySystemSession(RepositorySystem system) {
    DefaultRepositorySystemSession session = MavenRepositorySystemUtils.newSession();
    Path tempDir;
    try {
      tempDir = getRepositoryPath();
    } catch (IOException e) {
      throw new IllegalStateException("Could not create temporary directory for jar artifact credentials", e);
    }
    LocalRepository localRepo = new LocalRepository(tempDir.toAbsolutePath().toString());
    session.setLocalRepositoryManager(system.newLocalRepositoryManager(session, localRepo));
    return session;
  }

  private File getJarFile(String reference) {
    org.eclipse.aether.artifact.Artifact jarArtifact = new DefaultArtifact(reference);
    ArtifactRequest request = new ArtifactRequest(jarArtifact, Collections.singletonList(remoteRepository), null);
    ArtifactResult result;
    try {
      result = repositorySystem.resolveArtifact(repositorySystemSession, request);
    } catch (ArtifactResolutionException e) {
      throw new IllegalStateException("Failed to download jar artifact: {}", e);
    }
    return result.getArtifact().getFile();
  }
}
