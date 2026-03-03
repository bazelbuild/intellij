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
package com.google.idea.blaze.base.execlog

import com.google.idea.common.util.Datafiles
import org.jetbrains.plugins.textmate.api.TextMateBundleProvider

class ExeclogBundleProvider : TextMateBundleProvider {

  companion object {
    const val EXECLOG_FILE_EXTENSION = "bazel-execlog"
  }

  override fun getBundles(): List<TextMateBundleProvider.PluginBundle> {
    return listOf(TextMateBundleProvider.PluginBundle(EXECLOG_FILE_EXTENSION, Datafiles.resolve("textmate/execlog")))
  }
}
