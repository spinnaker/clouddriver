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

package com.netflix.spinnaker.clouddriver.alicloud;

import com.google.common.io.Files;
import com.google.gson.Gson;
import com.netflix.spinnaker.clouddriver.alicloud.provider.view.CommonProvider;
import java.io.File;
import java.nio.charset.Charset;
import java.util.Objects;

public class BaseTest {
  public static final String ACCOUNT = "test-account";
  public static final String REGION = "cn-test";

  public static String getFileContentToString(String filePath) {

    String realPath =
        Objects.requireNonNull(CommonProvider.class.getClassLoader().getResource(filePath))
            .getPath();
    try {
      return Files.toString(new File(realPath), Charset.forName("utf-8"));
    } catch (Exception e) {
      e.printStackTrace();
    }
    return "";
  }

  public <T> T fromMockData(String path, Class<T> classOfT) {
    return new Gson().fromJson(getFileContentToString(path), classOfT);
  }
}
