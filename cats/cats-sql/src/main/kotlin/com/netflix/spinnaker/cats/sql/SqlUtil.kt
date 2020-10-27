/*
 * Copyright 2020 Armory
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

package com.netflix.spinnaker.cats.sql

import org.jooq.DSLContext
import org.jooq.SQLDialect

object SqlUtil {

  fun createTableLike(jooq: DSLContext, tablename: String, template: String) {
    when (jooq.dialect()) {
      SQLDialect.POSTGRES ->
        jooq.execute("CREATE TABLE IF NOT EXISTS $tablename (LIKE $template INCLUDING ALL)")
      else ->
        jooq.execute(
          "CREATE TABLE IF NOT EXISTS $tablename LIKE $template"
        )
    }
  }
}
