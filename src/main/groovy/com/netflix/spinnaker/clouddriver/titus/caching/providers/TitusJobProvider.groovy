/*
 * Copyright 2016 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.titus.caching.providers

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.clouddriver.aws.provider.view.AmazonS3DataProvider
import com.netflix.spinnaker.clouddriver.model.JobProvider
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import com.netflix.spinnaker.clouddriver.titus.TitusClientProvider
import com.netflix.spinnaker.clouddriver.titus.client.TitusClient
import com.netflix.spinnaker.clouddriver.titus.client.model.Job
import com.netflix.spinnaker.clouddriver.titus.model.TitusJobStatus
import okhttp3.OkHttpClient
import okhttp3.Request
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLSession

@Component
class TitusJobProvider implements JobProvider<TitusJobStatus> {

  String platform = "titus"

  TitusClientProvider titusClientProvider

  @Autowired
  ObjectMapper objectMapper

  @Autowired
  AmazonS3DataProvider amazonS3DataProvider

  OkHttpClient client = new OkHttpClient.Builder()
    .hostnameVerifier(new HostnameVerifier() {
    @Override
    boolean verify(String hostname, SSLSession session) {
      true
    }
  }).build();

  @Autowired
  AccountCredentialsProvider accountCredentialsProvider

  @Autowired
  TitusJobProvider(TitusClientProvider titusClientProvider) {
    this.titusClientProvider = titusClientProvider
  }

  @Override
  TitusJobStatus collectJob(String account, String location, String id) {
    TitusClient titusClient = titusClientProvider.getTitusClient(accountCredentialsProvider.getCredentials(account), location)
    Job job = titusClient.getJob(id)
    new TitusJobStatus(job, account, location)
  }

  @Override
  Map<String, Object> getFileContents(String account, String location, String id, String fileName) {
    TitusClient titusClient = titusClientProvider.getTitusClient(accountCredentialsProvider.getCredentials(account), location)
    Job job = titusClient.getJob(id)

    def fileContents

    if (job.tasks.last().logLocation != null && job.tasks.last().logLocation.containsKey("s3")) {
      HashMap s3 = job.tasks.last().logLocation.get("s3")
      OutputStream outputStream = new ByteArrayOutputStream()
      try {
        amazonS3DataProvider.getAdhocData("titus", "${s3.accountName}:${s3.region}:${s3.bucket}", "${s3.key}/${fileName}", outputStream)
      } catch(Exception e){
        throw new RuntimeException("Could not load ${fileName} for task ${job.tasks.last().id}")
      }
      fileContents = new ByteArrayInputStream(outputStream.toByteArray())
    } else {
      Map files = titusClient.logsDownload(job.tasks.last().id)
      if (!files.containsKey(fileName)) {
        throw new RuntimeException("File ${fileName} not found for task ${job.tasks.last().id}")
      }
      fileContents = client.newCall(new Request.Builder().url(files.get(fileName) as String).build()).execute().body().byteStream()
    }

    if (fileContents) {
      Map results = [:]
      if (fileName.endsWith('.json')) {
        results = objectMapper.readValue(fileContents, Map)
      } else {
        Properties propertiesFile = new Properties()
        propertiesFile.load(fileContents)
        results = results << propertiesFile
      }
      return results
    }
    null
  }

}
