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
package com.google.idea.blaze.base.plugin;

import com.google.common.annotations.VisibleForTesting;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.openapi.extensions.PluginId;

/**
 * Blaze sync plugin ID and version information.
 */
public class Version {

  public static class PluginInfo {
    public final String id;
    public final String version;

    @VisibleForTesting
    public static final PluginInfo UNKNOWN = new PluginInfo("UNKNOWN_PLUGIN", "UNKNOWN_VERSION");

    public PluginInfo(String id, String version) {
      this.id = id;
      this.version = version;
    }
  }

  public static PluginInfo getSyncPluginInfo() {
    BlazePluginId idService = BlazePluginId.getInstance();
    if (idService != null) {
      PluginId pluginId = PluginId.getId(idService.getPluginId());
      IdeaPluginDescriptor pluginInfo = PluginManager.getPlugin(pluginId);
      if (pluginInfo != null) {
        return new PluginInfo(pluginId.getIdString(), pluginInfo.getVersion());
      }
    }
    return PluginInfo.UNKNOWN;
  }
}
