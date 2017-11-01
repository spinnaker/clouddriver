/*
 * Copyright 2016 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.appengine.deploy.ops

import com.netflix.spinnaker.clouddriver.appengine.AppengineJobExecutor
import com.netflix.spinnaker.clouddriver.appengine.deploy.AppengineMutexRepository
import com.netflix.spinnaker.clouddriver.appengine.deploy.AppengineServerGroupNameResolver
import com.netflix.spinnaker.clouddriver.appengine.deploy.description.DeployAppengineDescription
import com.netflix.spinnaker.clouddriver.appengine.deploy.exception.AppengineOperationException
import com.netflix.spinnaker.clouddriver.appengine.gcsClient.AppengineGcsRepositoryClient
import com.netflix.spinnaker.clouddriver.appengine.artifacts.GcsStorageService
import com.netflix.spinnaker.clouddriver.appengine.artifacts.config.StorageConfigurationProperties
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.deploy.DeploymentResult
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import org.springframework.beans.factory.annotation.Autowired

import java.nio.file.Paths

class DeployAppengineAtomicOperation implements AtomicOperation<DeploymentResult> {
  private static final String BASE_PHASE = "DEPLOY"

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  @Autowired
  AppengineJobExecutor jobExecutor

  @Autowired(required=false)
  StorageConfigurationProperties storageConfiguration

  @Autowired(required=false)
  GcsStorageService.Factory storageServiceFactory

  DeployAppengineDescription description
  boolean usesGcs

  DeployAppengineAtomicOperation(DeployAppengineDescription description) {
    this.description = description
    this.usesGcs = description.repositoryUrl.startsWith("gs://")
  }

  /**
   * curl -X POST -H "Content-Type: application/json" -d '[ { "createServerGroup": { "application": "myapp", "stack": "stack", "freeFormDetails": "details", "repositoryUrl": "https://github.com/organization/project.git", "branch": "feature-branch", "credentials": "my-appengine-account", "configFilepaths": ["app.yaml"] } } ]' "http://localhost:7002/appengine/ops"
   * curl -X POST -H "Content-Type: application/json" -d '[ { "createServerGroup": { "application": "myapp", "stack": "stack", "freeFormDetails": "details", "repositoryUrl": "https://github.com/organization/project.git", "branch": "feature-branch", "credentials": "my-appengine-account", "configFilepaths": ["app.yaml"], "promote": true, "stopPreviousVersion": true } } ]' "http://localhost:7002/appengine/ops"
   * curl -X POST -H "Content-Type: application/json" -d '[ { "createServerGroup": { "application": "myapp", "stack": "stack", "freeFormDetails": "details", "repositoryUrl": "https://github.com/organization/project.git", "branch": "feature-branch", "credentials": "my-appengine-account", "configFilepaths": ["runtime: python27\napi_version: 1\nthreadsafe: true\nmanual_scaling:\n  instances: 5\ninbound_services:\n - warmup\nhandlers:\n - url: /.*\n   script: main.app"],} } ]' "http://localhost:7002/appengine/ops"
   */
  @Override
  DeploymentResult operate(List priorOutputs) {
    def  baseDir = description.credentials.localRepositoryDirectory
    def  directoryPath = getFullDirectoryPath(baseDir, description.repositoryUrl)

    /*
    * We can't allow concurrent deploy operations on the same local repository.
    * If operation A checks out a new branch before operation B has run 'gcloud app deploy',
    * operation B will deploy using that new branch's source files.
    * */
    return AppengineMutexRepository.atomicWrapper(directoryPath, {
      task.updateStatus BASE_PHASE, "Initializing creation of version..."
      def result = new DeploymentResult()
      def newVersionName = deploy(cloneOrUpdateLocalRepository(directoryPath, 1))
      def region = description.credentials.region
      result.serverGroupNames = Arrays.asList("$region:$newVersionName".toString())
      result.serverGroupNameByRegion[region] = newVersionName
      return result
    })
  }

  String cloneOrUpdateLocalRepository(String directoryPath, Integer retryCount) {
    def repositoryUrl = description.repositoryUrl
    def directory = new File(directoryPath)
    def branch = description.branch
    def branchLogName = branch
    def repositoryClient

    if (usesGcs) {
      if (storageConfiguration == null) {
        throw new IllegalStateException(
            "GCS has been disabled. To enable it, configure storage.gcs.enabled=false and restart clouddriver.")
      }

      def applicationDirectoryRoot = description.applicationDirectoryRoot
      String credentialPath = ""
      if (description.storageAccountName != null && !description.storageAccountName.isEmpty()) {
        credentialPath = storageConfiguration.getAccount(description.storageAccountName).jsonPath
      }
      GcsStorageService storage = storageServiceFactory.newForCredentials(credentialPath)
      repositoryClient = new AppengineGcsRepositoryClient(repositoryUrl, directoryPath, applicationDirectoryRoot,
                                                          storage, jobExecutor)
      branchLogName = "(current)"
    } else {
      repositoryClient = description.credentials.gitCredentials.buildRepositoryClient(
        repositoryUrl,
        directoryPath,
        description.gitCredentialType
      )
    }

    try {
      if (!directory.exists()) {
        task.updateStatus BASE_PHASE, "Grabbing repository $repositoryUrl into local directory..."
        directory.mkdir()
        repositoryClient.initializeLocalDirectory()
      }
      task.updateStatus BASE_PHASE, "Fetching updates from $repositoryUrl for $branchLogName..."
      repositoryClient.updateLocalDirectoryWithVersion(branch)
    } catch (Exception e) {
      directory.deleteDir()
      if (retryCount > 0) {
        return cloneOrUpdateLocalRepository(directoryPath, retryCount - 1)
      } else {
        throw e
      }
    }
    return directoryPath
  }

  String deploy(String repositoryPath) {
    def project = description.credentials.project
    def accountEmail = description.credentials.serviceAccountEmail
    def region = description.credentials.region
    def applicationDirectoryRoot = description.applicationDirectoryRoot
    def serverGroupNameResolver = new AppengineServerGroupNameResolver(project, region, description.credentials)
    def versionName = serverGroupNameResolver.resolveNextServerGroupName(description.application,
                                                                         description.stack,
                                                                         description.freeFormDetails,
                                                                         false)
    def writtenFullConfigFilePaths = writeConfigFiles(description.configFiles, repositoryPath, applicationDirectoryRoot)
    def repositoryFullConfigFilePaths =
      (description.configFilepaths?.collect { Paths.get(repositoryPath, applicationDirectoryRoot ?: '.', it).toString() } ?: []) as List<String>
    def deployCommand = ["gcloud", "app", "deploy", *(repositoryFullConfigFilePaths + writtenFullConfigFilePaths)]
    deployCommand << "--version=$versionName"
    deployCommand << (description.promote ? "--promote" : "--no-promote")
    deployCommand << (description.stopPreviousVersion ? "--stop-previous-version": "--no-stop-previous-version")
    deployCommand << "--project=$project"
    deployCommand << "--account=$accountEmail"

    task.updateStatus BASE_PHASE, "Deploying version $versionName..."
    try {
      jobExecutor.runCommand(deployCommand)
    } catch (e) {
      throw new AppengineOperationException("Failed to deploy to App Engine with command ${deployCommand.join(' ')}: ${e.getMessage()}")
    } finally {
      deleteFiles(writtenFullConfigFilePaths)
    }
    task.updateStatus BASE_PHASE, "Done deploying version $versionName..."
    return versionName
  }

  static List<String> writeConfigFiles(List<String> configFiles, String repositoryPath, String applicationDirectoryRoot) {
    if (!configFiles) {
      return []
    } else {
      return configFiles.collect { configFile ->
        def name = UUID.randomUUID().toString()
        def path = Paths.get(repositoryPath, applicationDirectoryRoot ?: ".", "${name}.yaml")
        try {
          path.toFile() << configFile
        } catch(e) {
          throw new AppengineOperationException("Could not write config file: ${e.getMessage()}")
        }
        return path.toString()
      }
    }
  }

  static void deleteFiles(List<String> paths) {
    paths.each { path ->
      try {
        new File(path).delete()
      } catch(e) {
        throw new AppengineOperationException("Could not delete config file: ${e.getMessage()}")
      }
    }
  }

  static String getFullDirectoryPath(String localRepositoryDirectory, String repositoryUrl) {
    return Paths.get(localRepositoryDirectory, repositoryUrl.replace('/', '-')).toString()
  }
}
