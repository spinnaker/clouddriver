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
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.springframework.scheduling.annotation.Scheduled;

@Slf4j
public class GitRepoFileSystem {
  private static final Path CLONES_HOME =
      Paths.get(System.getProperty("java.io.tmpdir"), "gitrepos");

  private final GitRepoArtifactProviderProperties config;
  private final Map<String, Lock> pathLocks = new ConcurrentHashMap<>();

  public GitRepoFileSystem(GitRepoArtifactProviderProperties properties) {
    this.config = properties;
  }

  public Path getLocalClonePath(String repoUrl, String branch) {
    return Paths.get(CLONES_HOME.toString(), hashCoordinates(repoUrl, branch));
  }

  public int getCloneWaitLockTimeoutSec() {
    return config.getCloneWaitLockTimeoutSec();
  }

  public boolean tryTimedLock(String repoUrl, String branch) throws InterruptedException {
    String hash = hashCoordinates(repoUrl, branch);

    log.debug(
        "Trying filesystem timed lock for {} (branch {}), hash: {} for {} seconds",
        repoUrl,
        branch,
        hash,
        config.getCloneWaitLockTimeoutSec());

    Lock lock = createOrGetLock(hash);
    boolean locked = lock.tryLock(config.getCloneWaitLockTimeoutSec(), TimeUnit.SECONDS);
    log.debug(
        "Lock {} acquired for {} (branch {}), hash {}, lock instance: {}",
        (locked ? "" : "NOT"),
        repoUrl,
        branch,
        hash,
        lock);
    return locked;
  }

  private synchronized Lock createOrGetLock(String hash) {
    if (!pathLocks.containsKey(hash)) {
      log.debug("Creating new lock instance for hash: {}", hash);
      pathLocks.put(hash, new ReentrantLock());
    }
    return pathLocks.get(hash);
  }

  public boolean tryLock(String cloneHashDir) {
    log.debug("Trying filesystem lock for hash: {}", cloneHashDir);
    Lock lock = createOrGetLock(cloneHashDir);
    boolean locked = lock.tryLock();
    log.debug("Lock {} acquired for hash {}", (locked ? "" : "NOT"), cloneHashDir);
    return locked;
  }

  public void unlock(String repoUrl, String branch) {
    String hash = hashCoordinates(repoUrl, branch);
    log.debug("Unlocking filesystem for {} (branch {}), hash: {}", repoUrl, branch, hash);
    unlock(hash);
  }

  public synchronized void unlock(String cloneHashDir) {
    if (!pathLocks.containsKey(cloneHashDir)) {
      log.warn(
          "Attempting to unlock filesystem with hash {} that doesn't have a lock", cloneHashDir);
      return;
    }
    Lock lock = pathLocks.get(cloneHashDir);
    log.debug("Unlocking filesystem for hash {}, lock instance: {}", cloneHashDir, lock);
    lock.unlock();
  }

  public boolean canRetainClone() {
    return config.getCloneRetentionMinutes() != 0 && hasFreeDisk();
  }

  private boolean hasFreeDisk() {
    long currentSize = 0;
    if (CLONES_HOME.toFile().exists()) {
      currentSize = FileUtils.sizeOfDirectory(CLONES_HOME.toFile());
    }
    return currentSize >= 0 && currentSize < config.getCloneRetentionMaxBytes();
  }

  private String hashCoordinates(String repoUrl, String branch) {
    String coordinates =
        String.format(
            "%s-%s",
            Optional.ofNullable(repoUrl).orElse("unknownUrl"),
            Optional.ofNullable(branch).orElse("defaultBranch"));
    return Hashing.sha256().hashString(coordinates, Charset.defaultCharset()).toString();
  }

  @Scheduled(
      fixedDelayString =
          "${artifacts.git-repo.clone-retention-check-ms:"
              + GitRepoArtifactProviderProperties.DEFAULT_CLONE_RETENTION_CHECK_MS
              + "}")
  private void deleteExpiredRepos() {
    try {
      if (!CLONES_HOME.toFile().exists() || config.getCloneRetentionMinutes() < 0) {
        return;
      }
      File[] repos = CLONES_HOME.toFile().listFiles();
      if (repos == null) {
        return;
      }
      for (File r : repos) {
        long ageMin = ((System.currentTimeMillis() - r.lastModified()) / 1000) / 60;
        if (ageMin < config.getCloneRetentionMinutes()) {
          continue;
        }
        if (!tryLock(r.getName())) {
          // move on if the directory is locked by another thread, just wait for the next cycle
          continue;
        }
        try {
          log.info("Deleting expired git clone {}", r.getName());
          FileUtils.forceDelete(r);
        } finally {
          unlock(r.getName());
        }
      }
    } catch (IOException e) {
      log.error("Error deleting expired git clones, ignoring", e);
    }
  }
}
