/*
 * Copyright 2018 Schibsted ASA.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.aws.model;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.netflix.spinnaker.clouddriver.model.Image;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
public class AmazonImage implements Image {
  public static final String AMAZON_IMAGE_TYPE = "aws/image";

  String region;
  List<AmazonServerGroup> serverGroups = new ArrayList<>();
  @JsonUnwrapped
  com.amazonaws.services.ec2.model.Image image;

  public String getName() {
    return image.getName();
  }

  public String getId() {
    return image.getImageId();
  }
}
