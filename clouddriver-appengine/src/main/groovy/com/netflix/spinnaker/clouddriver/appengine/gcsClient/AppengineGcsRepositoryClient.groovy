/*
 * Copyright 2017 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.appengine.gcsClient

import com.netflix.spinnaker.clouddriver.appengine.AppengineJobExecutor
import com.netflix.spinnaker.clouddriver.appengine.model.AppengineRepositoryClient
import com.netflix.spinnaker.clouddriver.appengine.storage.GcsStorageService
import com.netflix.spinnaker.clouddriver.appengine.storage.StorageUtils
import groovy.transform.TupleConstructor
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.io.IOUtils

@TupleConstructor
class AppengineGcsRepositoryClient implements AppengineRepositoryClient {
  String repositoryUrl
  String targetDirectory
  String applicationDirectoryRoot
  GcsStorageService storage
  AppengineJobExecutor jobExecutor

  void initializeLocalDirectory() {
    downloadFiles()
  }

  void updateLocalDirectoryWithVersion(String version) {
    downloadFiles()
  }

  void downloadFiles() {
    def gsPrefix = "gs://"
    if (!repositoryUrl.startsWith(gsPrefix)) {
      throw new IllegalArgumentException("Repository is not a GCS bucket: " + repositoryUrl)
    }

    def dest = targetDirectory + File.separator + applicationDirectoryRoot

    def fullPath = repositoryUrl.substring(gsPrefix.length())
    if (applicationDirectoryRoot) {
      fullPath + '/' + applicationDirectoryRoot
    }
    def slash = fullPath.indexOf("/")
    def bucketName = fullPath.substring(0, slash)
    def bucketPath = fullPath.substring(slash + 1)

    if (fullPath.endsWith(".tar")) {
      InputStream tas = storage.openObjectStream(bucketName, bucketPath)

      // NOTE: We write the tar file out to an intermediate temp file because the tar input stream
      // directly from openObjectStream() closes unexpectedly when accessed from untarStreamToPath()
      // for some reason.
      File tempFile = File.createTempFile("app", "tar")
      FileOutputStream fos = new FileOutputStream(tempFile)
      IOUtils.copy(tas, fos)
      tas.close()
      fos.close()

      StorageUtils.untarStreamToPath(new FileInputStream(tempFile), dest)
      tempFile.delete()
    } else {
      storage.visitObjects(bucketName, bucketPath, { obj -> storage.downloadStorageObject(obj, dest) })
    }
  }
}
