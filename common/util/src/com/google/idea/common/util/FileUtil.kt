/*
 * Copyright 2026 The Bazel Authors. All rights reserved.
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
package com.google.idea.common.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

object FileUtil {

  suspend inline fun deleteOnException(path: Path, crossinline body: suspend () -> Unit) {
    try {
      body()
    } catch (t: Throwable) {
      try {
        withContext(Dispatchers.IO) {
          Files.deleteIfExists(path)
        }
      } catch (e: IOException) {
        // best effort cleanup
      }
      throw t
    }
  }
}