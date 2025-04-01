/*
 * Copyright 2024 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.sync.aspects.storage

import com.google.common.io.ByteSource

object AspectRepositoryProvider {

  const val ASPECT_DIRECTORY: String = "aspect/default"
  const val ASPECT_TEMPLATE_DIRECTORY: String = "aspect/template"
  const val ASPECT_QSYNC_DIRECTORY: String = "aspect/qsync"

  @JvmStatic
  fun aspectQSyncFile(file: String): ByteSource {
    return AspectWriter.readAspect(AspectRepositoryProvider::class.java, ASPECT_QSYNC_DIRECTORY, file)
  }
}
