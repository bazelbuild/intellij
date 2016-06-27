/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.android.plugin;

import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.io.CharStreams;
import com.intellij.notification.NotificationDisplayType;
import com.intellij.notification.NotificationGroup;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.util.BuildNumber;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

/**
 * Checks META-INF/product-build.txt for a product build number and compares
 * them against the build. If incompatible, it informs the user.
 */
public class PluginCompatibilityEnforcer implements ApplicationComponent {
  private static final Logger LOG = Logger.getInstance(PluginCompatibilityEnforcer.class);
  private static final NotificationGroup NOTIFICATION_GROUP =
    new NotificationGroup("ASwB Plugin Version", NotificationDisplayType.BALLOON, true);

  public void checkPluginCompatibility() {
    String pluginProductBuildString = readProductBuildTxt();
    if (Strings.isNullOrEmpty(pluginProductBuildString)) {
      return;
    }
    // Dev mode?
    if (pluginProductBuildString.equals("PRODUCT_BUILD")) {
      return;
    }
    BuildNumber pluginProductBuild = BuildNumber.fromString(pluginProductBuildString);
    if (pluginProductBuild == null) {
      LOG.warn("Invalid META-INF/product-build.txt");
      return;
    }

    if (!isCompatible(pluginProductBuild)) {
      String message = Joiner.on(' ').join(
        "Invalid Android Studio version for the ASwB plugin.",
        "Android Studio version: " + ApplicationInfo.getInstance().getBuild(),
        "Compatible version: " + pluginProductBuild,
        "Please update the ASwB plugin from the plugin manager."
      );
      NOTIFICATION_GROUP.createNotification(message, MessageType.ERROR).notify(null);
      LOG.warn(message);
    }
  }

  private boolean isCompatible(BuildNumber pluginProductBuild) {
    if (pluginProductBuild.isSnapshot()) {
      return true;
    }
    BuildNumber buildNumber = ApplicationInfo.getInstance().getBuild();
    if (buildNumber == null || buildNumber.isSnapshot()) {
      return true;
    }
    return buildNumber.equals(pluginProductBuild);
  }

  @Nullable
  private String readProductBuildTxt() {
    try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream("META-INF/product-build.txt")) {
      if (inputStream == null) {
        return null;
      }
      return CharStreams.toString(new InputStreamReader(inputStream)).trim();
    } catch (IOException e) {
      LOG.error("Could not read META-INF/product-build.txt", e);
      return null;
    }
  }

  @Override
  public void initComponent() {
    checkPluginCompatibility();
  }

  @Override
  public void disposeComponent() {

  }

  @NotNull
  @Override
  public String getComponentName() {
    return "ASwB plugin compatibility enforcer";
  }
}
