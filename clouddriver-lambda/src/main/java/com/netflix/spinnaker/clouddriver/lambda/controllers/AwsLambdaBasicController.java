/*
 * Copyright 2018 Amazon.com, Inc. or its affiliates.
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

package com.netflix.spinnaker.clouddriver.lambda.controllers;

import com.netflix.spinnaker.clouddriver.lambda.cache.model.AwsLambdaCacheModel;
import com.netflix.spinnaker.clouddriver.lambda.provider.view.AwsLambdaProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Collection;

@RestController
@RequestMapping("/awslambda")
public class AwsLambdaBasicController {

  AwsLambdaProvider awsLambdaProvider;

  @Autowired
  public AwsLambdaBasicController(AwsLambdaProvider awsLambdaProvider) {
    this.awsLambdaProvider = awsLambdaProvider;
  }

  @RequestMapping(value = "/getfunction", method = RequestMethod.GET)
  public Collection<AwsLambdaCacheModel> getFunction(@RequestParam(value="functionname",required = false) String functionname,
                                                     @RequestParam(value="accountname",required = false) String accountname,
                                                     @RequestParam(value="region",required = false) String region) {
    if (functionname == null && accountname == null && region == null) {
      return awsLambdaProvider.getAllAwsLambdaFunctions();
    }
    else if (functionname == null && accountname != null && region != null) {
      return awsLambdaProvider.getAllAwsLambdaFunctions(accountname, region);
    }
    else {
      ArrayList<AwsLambdaCacheModel> cache = new ArrayList<>();
      cache.add(awsLambdaProvider.getAwsLambdaFunction(functionname,region,accountname));
      return cache;
    }

  }

}
