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
package com.google.idea.blaze.android.sync;

import com.google.common.collect.Iterables;
import com.google.idea.blaze.android.projectview.AndroidSdkPlatformSection;
import com.google.idea.blaze.android.settings.AswbGlobalSettings;
import com.google.idea.blaze.android.sync.model.AndroidSdkPlatform;
import com.google.idea.blaze.android.sync.model.BlazeAndroidSyncData;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.projectview.ProjectViewManager;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.projectview.section.ScalarSection;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.output.IssueOutput;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.android.sdk.AndroidSdkAdditionalData;
import org.jetbrains.android.sdk.AndroidSdkUtils;

import javax.annotation.Nullable;
import java.io.File;
import java.util.Collection;

/**
 * Calculates AndroidSdkPlatform.
 */
public class AndroidSdkPlatformSyncer {
  @Nullable
  static AndroidSdkPlatform getAndroidSdkPlatform(
    Project project,
    BlazeContext context,
    File androidPlatformDirectory) {

    String androidSdk = null;

    String localSdkLocation = AswbGlobalSettings.getInstance().getLocalSdkLocation();
    if (localSdkLocation == null) {
      IssueOutput
        .error("Error: No android_sdk synced yet. Please sync SDK following go/aswb-sdk.")
        .submit(context);
    }

    ProjectViewSet projectViewSet = ProjectViewManager.getInstance(project).getProjectViewSet();
    if (projectViewSet != null) {
      Collection<ScalarSection<String>> androidSdkPlatformSections =
        projectViewSet.getSections(AndroidSdkPlatformSection.KEY);
      if (!androidSdkPlatformSections.isEmpty()) {
        ScalarSection<String> androidSdkPlatformSection = Iterables.getLast(androidSdkPlatformSections);
        androidSdk = BlazeAndroidSdk.getAndroidSdkLevelFromLocalChannel(
          localSdkLocation,
          androidSdkPlatformSection.getValue());

        if (androidSdk == null) {
          IssueOutput
            .error("No such android_sdk_platform: " + androidSdkPlatformSection.getValue())
            .inFile(projectViewSet.getTopLevelProjectViewFile().projectViewFile)
            .submit(context);
        }
      }
    }

    if (androidSdk == null) {
      androidSdk = BlazeAndroidSdk.getAndroidSdkLevelFromBlazeRc(androidPlatformDirectory);
    }

    if (androidSdk == null) {
      IssueOutput
        .error("Can't determine your SDK. Please sync your SDK by following go/aswb-sdk and try again.")
        .submit(context);
      return null;
    }

    Sdk sdk = AndroidSdkUtils.findSuitableAndroidSdk(androidSdk);
    if (sdk == null) {
      IssueOutput
        .error("Can't find a matching SDK. Please sync your SDK by following go/aswb-sdk and try again.")
        .submit(context);
      return null;
    }

    int androidSdkApiLevel = getAndroidSdkApiLevel(androidSdk);
    return new AndroidSdkPlatform(androidSdk, androidSdkApiLevel);
  }

  @Nullable
  static public AndroidSdkPlatform getAndroidSdkPlatform(BlazeProjectData blazeProjectData) {
    BlazeAndroidSyncData syncData = blazeProjectData.syncState.get(BlazeAndroidSyncData.class);
    return syncData != null ? syncData.androidSdkPlatform : null;
  }

  private static int getAndroidSdkApiLevel(String androidSdk) {
    int androidSdkApiLevel = 1;
    Sdk sdk = AndroidSdkUtils.findSuitableAndroidSdk(androidSdk);
    if (sdk != null) {
      AndroidSdkAdditionalData additionalData = (AndroidSdkAdditionalData)sdk.getSdkAdditionalData();
      if (additionalData != null) {
        AndroidPlatform androidPlatform = additionalData.getAndroidPlatform();
        if (androidPlatform != null) {
          androidSdkApiLevel = androidPlatform.getApiLevel();
        }
      }
    }
    return androidSdkApiLevel;
  }
}
