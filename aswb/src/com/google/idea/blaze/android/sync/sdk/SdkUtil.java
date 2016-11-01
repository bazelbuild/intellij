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
package com.google.idea.blaze.android.sync.sdk;

import com.android.tools.idea.updater.configure.SdkUpdaterConfigurableProvider;
import com.google.idea.blaze.android.sync.model.AndroidSdkPlatform;
import com.google.idea.blaze.android.sync.model.BlazeAndroidSyncData;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.options.ex.ConfigurableExtensionPointUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** SDK utilities. */
public class SdkUtil {
  @Nullable
  public static AndroidSdkPlatform getAndroidSdkPlatform(BlazeProjectData blazeProjectData) {
    BlazeAndroidSyncData syncData = blazeProjectData.syncState.get(BlazeAndroidSyncData.class);
    return syncData != null ? syncData.androidSdkPlatform : null;
  }

  @Nullable
  public static AndroidPlatform getAndroidPlatform(@NotNull Project project) {
    BlazeProjectData blazeProjectData =
        BlazeProjectDataManager.getInstance(project).getBlazeProjectData();
    if (blazeProjectData == null) {
      return null;
    }
    AndroidSdkPlatform androidSdkPlatform = getAndroidSdkPlatform(blazeProjectData);
    if (androidSdkPlatform == null) {
      return null;
    }
    Sdk sdk = AndroidSdkUtils.findSuitableAndroidSdk(androidSdkPlatform.androidSdk);
    if (sdk == null) {
      return null;
    }
    return AndroidPlatform.getInstance(sdk);
  }

  /** Opens the SDK manager settings page */
  public static void openSdkManager() {
    Configurable configurable =
        ConfigurableExtensionPointUtil.createApplicationConfigurableForProvider(
            SdkUpdaterConfigurableProvider.class);
    ShowSettingsUtil.getInstance().showSettingsDialog(null, configurable.getClass());
  }
}
