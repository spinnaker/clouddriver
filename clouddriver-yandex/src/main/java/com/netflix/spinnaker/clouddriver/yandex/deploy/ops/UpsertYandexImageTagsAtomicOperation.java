/*
 * Copyright 2020 YANDEX LLC
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

package com.netflix.spinnaker.clouddriver.yandex.deploy.ops;

import static yandex.cloud.api.compute.v1.ImageServiceOuterClass.*;

import com.google.common.base.Strings;
import com.google.protobuf.FieldMask;
import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import com.netflix.spinnaker.clouddriver.yandex.deploy.YandexOperationPoller;
import com.netflix.spinnaker.clouddriver.yandex.deploy.description.UpsertYandexImageTagsDescription;
import com.netflix.spinnaker.clouddriver.yandex.security.YandexCloudCredentials;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import yandex.cloud.api.compute.v1.ImageOuterClass;
import yandex.cloud.api.operation.OperationOuterClass;

public class UpsertYandexImageTagsAtomicOperation implements AtomicOperation<Void> {
  private static final String BASE_PHASE = "UPSERT_IMAGE_TAGS";
  private final UpsertYandexImageTagsDescription description;
  @Autowired private YandexOperationPoller operationPoller;

  public UpsertYandexImageTagsAtomicOperation(UpsertYandexImageTagsDescription description) {
    this.description = description;
  }

  @Override
  public Void operate(List priorOutputs) {
    Task task = TaskRepository.threadLocalTask.get();
    task.updateStatus(
        BASE_PHASE, "Initializing upsert of image tags for " + description.getImageName() + "...");

    ImageOuterClass.Image image = getImage(description.getImageName());

    if (image != null) {
      Map<String, String> labels = new HashMap<>(image.getLabelsMap());
      labels.putAll(description.getTags());

      task.updateStatus(
          BASE_PHASE,
          "Upserting new labels "
              + labels
              + " in place of original labels "
              + image.getLabelsMap()
              + " for image "
              + description.getImageName()
              + "...");
      OperationOuterClass.Operation operation =
          description
              .getCredentials()
              .imageService()
              .update(
                  UpdateImageRequest.newBuilder()
                      .setImageId(image.getId())
                      .setUpdateMask(FieldMask.newBuilder().addPaths("labels").build())
                      .putAllLabels(labels)
                      .build());
      operationPoller.waitDone(description.getCredentials(), operation, BASE_PHASE);
    }

    task.updateStatus(BASE_PHASE, "Done tagging image " + description.getImageName() + ".");
    return null;
  }

  private ImageOuterClass.Image getImage(String imageName) {
    YandexCloudCredentials credentials = description.getCredentials();

    List<ImageOuterClass.Image> images = new ArrayList<>();
    String nextPageToken = "";
    do {
      ListImagesRequest request =
          ListImagesRequest.newBuilder()
              .setFolderId(credentials.getFolder())
              .setFilter("name='" + imageName + "'")
              .setPageToken(nextPageToken)
              .build();
      ListImagesResponse response = credentials.imageService().list(request);
      images.addAll(response.getImagesList());
      nextPageToken = response.getNextPageToken();
    } while (!Strings.isNullOrEmpty(nextPageToken));
    return images.stream().filter(i -> imageName.equals(i.getName())).findFirst().orElse(null);
  }
}
