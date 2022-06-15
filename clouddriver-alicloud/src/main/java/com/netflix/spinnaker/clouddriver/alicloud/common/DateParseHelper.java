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

package com.netflix.spinnaker.clouddriver.alicloud.common;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class DateParseHelper {
  static SimpleDateFormat YYYY_MM_DD_HH_MM_SS_UTC = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm'Z'");

  public static Date parseUTCTime(String utcTime) throws ParseException {
    if (utcTime == null || utcTime.length() == 0) {
      return null;
    }

    if (utcTime.endsWith("UTC")) {
      utcTime = utcTime.replace("Z", " UTC");
    }
    return YYYY_MM_DD_HH_MM_SS_UTC.parse(utcTime);
  }

  public static String format(Date date) {
    if (date == null) {
      return null;
    }
    return YYYY_MM_DD_HH_MM_SS_UTC.format(date);
  }
}
