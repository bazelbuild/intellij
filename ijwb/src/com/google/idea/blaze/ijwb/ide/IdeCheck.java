/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.ijwb.ide;

import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.openapi.extensions.PluginId;

/**
 * IDE and plugin checks.
 */
public class IdeCheck {
  public static boolean isPluginEnabled(String pluginIdString) {
    PluginId pluginId = PluginId.getId(pluginIdString);
    if (!PluginManager.isPluginInstalled(pluginId)) {
      return false;
    }
    IdeaPluginDescriptor plugin = PluginManager.getPlugin(pluginId);
    if (plugin == null) {
      return false;
    }
    return plugin.isEnabled();
  }
}
