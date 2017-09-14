/*
 * Copyright 2017 Lookout, Inc.
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

package com.netflix.spinnaker.clouddriver.ecs.controllers;

import com.amazonaws.services.ecr.AmazonECR;
import com.amazonaws.services.ecr.model.DescribeImagesRequest;
import com.amazonaws.services.ecr.model.DescribeImagesResult;
import com.amazonaws.services.ecr.model.ImageDetail;
import com.amazonaws.services.ecr.model.ListImagesRequest;
import com.amazonaws.services.ecr.model.ListImagesResult;
import com.amazonaws.services.ecr.model.Repository;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.netflix.spinnaker.clouddriver.aws.controllers.AmazonNamedImageLookupController;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider;
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials;
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAssumeRoleAmazonCredentials;
import com.netflix.spinnaker.clouddriver.security.AccountCredentials;
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider;
import com.netflix.spinnaker.kork.web.exceptions.NotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/ecs")
public class EcsFindImagesByTagController {

  AmazonClientProvider amazonClientProvider;

  AccountCredentialsProvider accountCredentialsProvider;

  @Autowired
  public EcsFindImagesByTagController(AmazonClientProvider amazonClientProvider,
                       AccountCredentialsProvider accountCredentialsProvider) {
    this.amazonClientProvider = amazonClientProvider;
    this.accountCredentialsProvider = accountCredentialsProvider;
  }

  private NetflixAmazonCredentials getCredentials(String accountId) {
    for (AccountCredentials credentials: accountCredentialsProvider.getAll()) {
      if (credentials instanceof NetflixAmazonCredentials) {
        NetflixAmazonCredentials amazonCredentials = (NetflixAmazonCredentials) credentials;
        if (amazonCredentials.getAccountId().equals(accountId)) {
          return amazonCredentials;
        }
      }
    }



    throw new NotFoundException(String.format("AWS account %s was not found.  Please specify a valid account name"));
  }

  @RequestMapping(value = "/images/find", method = RequestMethod.GET)
  public Object findImage(@RequestParam("q") String dockerImageUrl, HttpServletRequest request) {
    String accountId = dockerImageUrl.split("\\.")[0];
    String tag = dockerImageUrl.split("\\:")[1];
    NetflixAmazonCredentials credentials = getCredentials(accountId);

    AmazonECR amazonECR = amazonClientProvider.getAmazonEcr(credentials.getName(), credentials.getCredentialsProvider(), "us-west-2");

    ListImagesResult result = amazonECR.listImages(new ListImagesRequest().withRegistryId(accountId).withRepositoryName("continuous-delivery"));
    DescribeImagesResult imagesResult = amazonECR.describeImages(new DescribeImagesRequest().withRegistryId(accountId).withRepositoryName("continuous-delivery").withImageIds(result.getImageIds()));

    List<ImageDetail> imagesWithThisTag = new ArrayList<>();

    for (ImageDetail imageDetail: imagesResult.getImageDetails()) {
      if (imageDetail.getImageTags().contains(tag)) {  // TODO - what is the user interface we want to have here?  We should discuss with Lars and Ethan from the community as this whole thing will undergo a big refactoring
        imagesWithThisTag.add(imageDetail);
      }
    }

    if (imagesWithThisTag.size() > 1) {
      throw new Error("More than 1 image has this tag!  We can't handle this in the POC!");
    } else if (imagesWithThisTag.size() == 0) {
      throw new Error(String.format("No image with the tag %s was found", tag));
    }



    List<Map<String, Object>> responseBody = new ArrayList<>();
    Map<String, Object> map = new HashMap<>();
    map.put("imageName", buildFullDockerImageUrl(imagesWithThisTag.get(0).getImageDigest(),
      imagesWithThisTag.get(0).getRegistryId(),
      imagesWithThisTag.get(0).getRepositoryName(),
      "us-west-2"));  // TODO - this is so cheesy, uncheese this

    Map<String, List<String>> amis = new HashMap<>();
    amis.put("us-west-2", Arrays.asList(imagesWithThisTag.get(0).getImageDigest()));
    map.put("amis", amis);

    responseBody.add(map);

    return responseBody;
  }


  private String buildFullDockerImageUrl(String imageDigest, String registryId, String repositoryName, String region) {
    StringBuilder stringBuilder = new StringBuilder();
    stringBuilder.append(registryId)
      .append(".dkr.ecr.")
      .append(region)
      .append(".amazonaws.com/")
      .append(repositoryName)
      .append("@")
      .append(imageDigest);
    return stringBuilder.toString();
  }
}
