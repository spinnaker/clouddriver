/*
 * Copyright 2020 Armory, Inc.
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

import static java.nio.charset.StandardCharsets.UTF_8;

import com.netflix.spinnaker.clouddriver.jobs.JobExecutor;
import com.netflix.spinnaker.clouddriver.jobs.JobRequest;
import com.netflix.spinnaker.clouddriver.jobs.JobResult;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Pattern;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.util.StringUtils;

@Slf4j
public class GitJobExecutor {

  private static final String SSH_KEY_PWD_ENV_VAR = "SSH_KEY_PWD";
  private static final Pattern SHA_PATTERN = Pattern.compile("[0-9a-f]{40}");
  private static Path genericAskPassBinary;

  @Getter private final GitRepoArtifactAccount account;
  private final JobExecutor jobExecutor;
  private final String gitExecutable;
  private final AuthType authType;
  private final Path askPassBinary;

  private enum AuthType {
    USER_PASS,
    TOKEN,
    SSH,
    NONE
  }

  public GitJobExecutor(
      GitRepoArtifactAccount account, JobExecutor jobExecutor, String gitExecutable)
      throws IOException {
    this.account = account;
    this.jobExecutor = jobExecutor;
    this.gitExecutable = gitExecutable;
    if (!StringUtils.isEmpty(account.getUsername())
        && !StringUtils.isEmpty(account.getPassword())) {
      authType = AuthType.USER_PASS;
    } else if (!StringUtils.isEmpty(account.getToken())) {
      authType = AuthType.TOKEN;
    } else if (!StringUtils.isEmpty(account.getSshPrivateKeyFilePath())) {
      authType = AuthType.SSH;
    } else {
      authType = AuthType.NONE;
    }
    askPassBinary = initAskPass();
  }

  public void cloneOrPull(String repoUrl, String branch, Path localPath, String repoBasename)
      throws IOException {
    File localPathFile = localPath.toFile();
    if (!localPathFile.exists()) {
      clone(repoUrl, branch, localPath, repoBasename);
      return;
    }
    // localPath exists

    if (!localPathFile.isDirectory()) {
      throw new IllegalArgumentException(
          "Local path " + localPath.toString() + " is not a directory");
    }
    // localPath exists and is a directory

    File[] localPathFiles = localPathFile.listFiles();
    if (localPathFiles == null || localPathFiles.length == 0) {
      clone(repoUrl, branch, localPath, repoBasename);
      return;
    }
    // localPath exists, is a directory and has files in it

    Path dotGitPath = Paths.get(localPath.toString(), repoBasename, ".git");
    if (!dotGitPath.toFile().exists()) {
      log.warn(
          "Directory {} for git/repo {}, branch {} has files or directories but {} was not found. The directory will be recreated to start with a new clone.",
          localPath.toString(),
          repoUrl,
          branch,
          dotGitPath.toString());
      clone(repoUrl, branch, localPath, repoBasename);
      return;
    }
    // localPath has "<repo>/.git" directory

    pull(repoUrl, branch, dotGitPath.getParent());
  }

  private void clone(String repoUrl, String branch, Path destination, String repoBasename)
      throws IOException {
    if (!isValidReference(repoUrl)) {
      throw new IllegalArgumentException(
          "Git reference \""
              + repoUrl
              + "\" is invalid for credentials with auth type "
              + authType);
    }

    File destinationFile = destination.toFile();
    if (destinationFile.exists()) {
      FileUtils.deleteDirectory(destinationFile);
    }
    FileUtils.forceMkdir(destinationFile);

    if (SHA_PATTERN.matcher(branch).matches()) {
      fetchSha(repoUrl, branch, destination, repoBasename);
    } else {
      cloneBranchOrTag(repoUrl, branch, destination);
    }
  }

  private void cloneBranchOrTag(String repoUrl, String branch, Path destination)
      throws IOException {
    log.info("Cloning git/repo {} into {}", repoUrl, destination.toString());

    String cloneCommand =
        gitExecutable + " clone --branch " + branch + " --depth 1 " + repoUrlWithAuth(repoUrl);

    List<String> command = cmdToList(cloneCommand);
    runCommand(
        command, destination, "Failed to clone repository " + repoUrl + " into " + destination);
  }

  private void fetchSha(String repoUrl, String sha, Path destination, String repoBasename)
      throws IOException {
    Path repoPath = Paths.get(destination.toString(), repoBasename);
    log.info("Fetching git/repo {} sha {} into {}", repoUrl, sha, destination.toString());

    if (!repoPath.toFile().mkdirs()) {
      throw new IOException("Unable to create directory " + repoPath.toString());
    }

    List<String> command = cmdToList(gitExecutable + " init");
    runCommand(command, repoPath, "Failed to initialize repository in " + repoPath.toString());

    command = cmdToList(gitExecutable + " remote add origin " + repoUrlWithAuth(repoUrl));
    runCommand(command, repoPath, "Failed adding repository origin " + repoUrl);

    command = cmdToList(gitExecutable + " fetch origin " + sha);
    JobResult<String> result = runCommandAndGetResult(command, repoPath);
    if (result.getResult() != JobResult.Result.SUCCESS) {
      // Some git servers don't allow to directly fetch specific commits
      // (error: Server does not allow request for unadvertised object),
      // this is a fallback to fetch everything first
      log.warn(
          "Unable to directly fetch specific sha, trying generic fetch. Error: "
              + result.getError());
      command = cmdToList(gitExecutable + " fetch origin");
      runCommand(command, repoPath, "Error running \"git fetch\"");
      command = cmdToList(gitExecutable + " fetch origin " + sha);
      runCommand(command, repoPath, "Unable to fetch sha " + sha);
    }

    command = cmdToList(gitExecutable + " reset --hard FETCH_HEAD");
    runCommand(command, repoPath, "Failed doing \"git reset --hard FETCH_HEAD\"" + sha);
  }

  private void pull(String repoUrl, String branch, Path localPath) throws IOException {
    if (SHA_PATTERN.matcher(branch).matches()) {
      log.info(
          "Contents of git/repo {} for sha {} already downloaded, no \"git pull\" needed.",
          repoUrl,
          branch);
      return;
    }

    log.info("Pulling git/repo {} into {}", repoUrl, localPath.toString());

    String cloneCommand = gitExecutable + " pull";
    List<String> command = cmdToList(cloneCommand);
    runCommand(
        command,
        localPath,
        "Failed on \"git pull\" of repository " + repoUrl + " into " + localPath);

    if (!localPath.getParent().toFile().setLastModified(System.currentTimeMillis())) {
      log.warn("Unable to set last modified time on {}", localPath.getParent().toString());
    }
  }

  public void archive(Path localClone, String branch, String subDir, Path outputFile)
      throws IOException {

    List<String> command =
        new ArrayList<>(
            Arrays.asList(
                gitExecutable,
                "archive",
                "--format",
                "tgz",
                "--output",
                outputFile.toString(),
                branch));
    if (!StringUtils.isEmpty(subDir)) {
      command.add(subDir);
    }

    runCommand(command, localClone, "Failed to archive repository from " + localClone);
  }

  private JobResult<String> runCommandAndGetResult(List<String> command, Path dir) {
    log.debug("Executing command: \"{}\"", String.join(" ", command));
    return jobExecutor.runJob(new JobRequest(command, addEnvVars(System.getenv()), dir.toFile()));
  }

  private void runCommand(List<String> command, Path dir, String errorMsg) throws IOException {
    JobResult<String> result = runCommandAndGetResult(command, dir);
    if (result.getResult() != JobResult.Result.SUCCESS) {
      throw new IOException(
          errorMsg + ". Error: " + result.getError() + " Output: " + result.getOutput());
    }
  }

  /**
   * For SSH authentication if the private key is password protected, SSH_ASKPASS binary is used to
   * supply the password. https://git-scm.com/docs/gitcredentials#_requesting_credentials
   */
  private Path initAskPass() throws IOException {
    if (authType != AuthType.SSH) {
      return null;
    }

    if (!StringUtils.isEmpty(account.getSshPrivateKeyPassphraseCmd())) {
      File pwdCmd = new File(account.getSshPrivateKeyPassphraseCmd());
      if (!pwdCmd.exists() || !pwdCmd.isFile()) {
        throw new IOException(
            "SshPrivateKeyPassphraseCmd doesn't exist or is not a file: "
                + account.getSshPrivateKeyPassphraseCmd());
      }
      return Paths.get(account.getSshPrivateKeyPassphraseCmd());
    }

    if (genericAskPassBinary == null) {
      File askpass = File.createTempFile("askpass", null);
      if (!askpass.setExecutable(true)) {
        throw new IOException(
            "Unable to make executable askpass script at " + askpass.toPath().toString());
      }

      // Default way for supplying the password of a private ssh key is to echo an env var with the
      // password.
      // This env var is set at runtime when executing git commands that need it.
      FileUtils.writeStringToFile(
          askpass,
          "#!/bin/sh\n" + "echo \"$" + SSH_KEY_PWD_ENV_VAR + "\"",
          Charset.defaultCharset());
      genericAskPassBinary = askpass.toPath();
    }

    return genericAskPassBinary;
  }

  private boolean isValidReference(String reference) {
    if (authType == AuthType.USER_PASS || authType == AuthType.TOKEN) {
      return reference.startsWith("http");
    }
    if (authType == AuthType.SSH) {
      return reference.startsWith("ssh://") || reference.startsWith("git@");
    }
    return true;
  }

  private List<String> cmdToList(String cmd) {
    List<String> cmdList = new ArrayList<>();
    switch (authType) {
      case USER_PASS:
      case TOKEN:
        // "sh" subshell is used so that environment variables can be used as part of the command
        cmdList.add("sh");
        cmdList.add("-c");
        cmdList.add(cmd);
        break;
      case SSH:
      default:
        cmdList.addAll(Arrays.asList(cmd.split(" ")));
        break;
    }
    return cmdList;
  }

  private String repoUrlWithAuth(String repoUrl) {
    if (authType != AuthType.USER_PASS && authType != AuthType.TOKEN) {
      return repoUrl;
    }

    String authPart;
    if (authType == AuthType.USER_PASS) {
      authPart = "$GIT_USER:$GIT_PASS";
    } else {
      authPart = "token:$GIT_TOKEN";
    }

    try {
      URI uri = new URI(repoUrl);
      return String.format(
          "%s://%s@%s%s%s",
          uri.getScheme(),
          authPart,
          uri.getHost(),
          (uri.getPort() > 0 ? ":" + uri.getPort() : ""),
          uri.getRawPath());
    } catch (URISyntaxException e) {
      throw new IllegalArgumentException("Malformed git repo url " + repoUrl, e);
    }
  }

  private Map<String, String> addEnvVars(Map<String, String> env) {
    Map<String, String> result = new HashMap<>(env);

    switch (authType) {
      case USER_PASS:
        result.put("GIT_USER", encodeURIComponent(account.getUsername()));
        result.put("GIT_PASS", encodeURIComponent(account.getPassword()));
        break;
      case TOKEN:
        result.put("GIT_TOKEN", encodeURIComponent(account.getToken()));
        break;
      case SSH:
        result.put("GIT_SSH_COMMAND", buildSshCommand());
        result.put("SSH_ASKPASS", askPassBinary.toString());
        result.put("DISPLAY", ":0");
        if (!StringUtils.isEmpty(account.getSshPrivateKeyPassphrase())) {
          result.put(SSH_KEY_PWD_ENV_VAR, account.getSshPrivateKeyPassphrase());
        }
        break;
    }

    if (log.isDebugEnabled()) {
      result.put("GIT_CURL_VERBOSE", "1");
      result.put("GIT_TRACE", "1");
    }
    return result;
  }

  @NotNull
  private String buildSshCommand() {
    String gitSshCmd = "setsid ssh";
    if (account.isSshTrustUnknownHosts()) {
      gitSshCmd += " -o UserKnownHostsFile=/dev/null -o StrictHostKeyChecking=no";
    } else if (!StringUtils.isEmpty(account.getSshKnownHostsFilePath())) {
      gitSshCmd += " -o UserKnownHostsFile=" + account.getSshKnownHostsFilePath();
    }
    if (!StringUtils.isEmpty(account.getSshPrivateKeyFilePath())) {
      gitSshCmd += " -i " + account.getSshPrivateKeyFilePath();
    }
    return gitSshCmd;
  }

  private static String encodeURIComponent(String s) {
    if (StringUtils.isEmpty(s)) {
      return s;
    }
    String result;
    result =
        URLEncoder.encode(s, UTF_8)
            .replaceAll("\\+", "%20")
            .replaceAll("\\*", "%2A")
            .replaceAll("%21", "!")
            .replaceAll("%27", "'")
            .replaceAll("%28", "(")
            .replaceAll("%29", ")")
            .replaceAll("%7E", "~");
    return result;
  }
}
