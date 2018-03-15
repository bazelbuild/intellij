/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.python;

import com.google.common.collect.ImmutableMap;
import com.intellij.util.PlatformUtils;

/** Utilities class related to the JetBrains python plugin. */
public final class PythonPluginUtils {

  private PythonPluginUtils() {}

  private static final ImmutableMap<String, String> PRODUCT_TO_PLUGIN_ID =
      ImmutableMap.of(
          PlatformUtils.IDEA_PREFIX,
          "Pythonid",
          PlatformUtils.IDEA_CE_PREFIX,
          "PythonCore",
          "AndroidStudio",
          "PythonCore",
          PlatformUtils.CLION_PREFIX,
          "com.intellij.clion-python");

  public static String getPythonPluginId() {
    String pluginId = PRODUCT_TO_PLUGIN_ID.get(PlatformUtils.getPlatformPrefix());
    if (pluginId == null) {
      throw new RuntimeException(
          "No python plugin ID for unhandled platform: " + PlatformUtils.getPlatformPrefix());
    }
    return pluginId;
  }
}
