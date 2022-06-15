/*
 * Copyright 2022 Alibaba Group.
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
package com.netflix.spinnaker.clouddriver.alicloud.provider.agent;

import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.IMAGES;
import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.NAMED_IMAGES;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.aliyuncs.ecs.model.v20140526.DescribeImagesResponse;
import com.aliyuncs.ecs.model.v20140526.DescribeImagesResponse.Image;
import com.aliyuncs.exceptions.ClientException;
import com.netflix.spinnaker.cats.agent.CacheResult;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.clouddriver.alicloud.cache.Keys;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

public class AliCloudImageCachingAgentTest extends CommonCachingAgentTest {

  private final String IMAGENAME = "imageName";
  private final String IMAGEID = "imageId";

  @Before
  public void testBefore() throws ClientException {
    when(client.getAcsResponse(any())).thenAnswer(new ImageAnswer());
  }

  @Test
  public void testLoadData() {
    AliCloudImageCachingAgent agent =
        new AliCloudImageCachingAgent(account, REGION, objectMapper, client);
    CacheResult result = agent.loadData(providerCache);
    String key = Keys.getImageKey(IMAGEID, ACCOUNT, REGION);
    String nameKey = Keys.getNamedImageKey(ACCOUNT, IMAGENAME);

    List<CacheData> images = (List) result.getCacheResults().get(IMAGES.ns);
    List<CacheData> namedImages = (List) result.getCacheResults().get(NAMED_IMAGES.ns);

    assertEquals(1, images.size());
    assertEquals(key, images.get(0).getId());

    assertEquals(1, namedImages.size());
    assertEquals(nameKey, namedImages.get(0).getId());
  }

  private class ImageAnswer implements Answer<DescribeImagesResponse> {
    @Override
    public DescribeImagesResponse answer(InvocationOnMock invocation) throws Throwable {
      DescribeImagesResponse response = new DescribeImagesResponse();
      List<Image> images = new ArrayList<>();
      Image image = new Image();
      image.setImageName(IMAGENAME);
      image.setImageId(IMAGEID);
      images.add(image);
      response.setImages(images);
      return response;
    }
  }
}
