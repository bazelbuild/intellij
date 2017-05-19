/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.plugin;

import com.google.common.collect.ImmutableSet;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.PluginsAdvertiser;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.pom.Navigatable;
import com.intellij.pom.NavigatableAdapter;

/** Utility methods for querying / manipulating other plugins. */
public final class PluginUtils {

  private PluginUtils() {}

  /** If the plugin is already installed, enable it, otherwise both install and enable it. */
  public static void installOrEnablePlugin(String pluginId) {
    if (isPluginInstalled(pluginId)) {
      PluginManager.enablePlugin(pluginId);
    } else {
      PluginsAdvertiser.installAndEnablePlugins(ImmutableSet.of(pluginId), EmptyRunnable.INSTANCE);
    }
  }

  /** Returns a {@link Navigatable} which will install (if necessary) and enable the given plugin */
  public static Navigatable installOrEnablePluginNavigable(String pluginId) {
    return new NavigatableAdapter() {
      @Override
      public void navigate(boolean requestFocus) {
        installOrEnablePlugin(pluginId);
      }
    };
  }

  public static boolean isPluginInstalled(String pluginId) {
    return getPluginDescriptor(pluginId) != null;
  }

  public static boolean isPluginEnabled(String pluginId) {
    IdeaPluginDescriptor descriptor = getPluginDescriptor(pluginId);
    return descriptor != null && descriptor.isEnabled();
  }

  private static IdeaPluginDescriptor getPluginDescriptor(String pluginId) {
    return PluginManager.getPlugin(PluginId.getId(pluginId));
  }
}
