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

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.google.idea.blaze.android.projectview.AndroidSdkPlatformSection;
import com.google.idea.blaze.android.sync.model.AndroidSdkPlatform;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.output.IssueOutput;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.pom.Navigatable;
import java.util.Collection;
import java.util.List;
import javax.annotation.Nullable;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.android.sdk.AndroidSdkAdditionalData;
import org.jetbrains.android.sdk.AndroidSdkUtils;

/** Calculates AndroidSdkPlatform. */
public class AndroidSdkFromProjectView {
  @Nullable
  public static AndroidSdkPlatform getAndroidSdkPlatform(
      BlazeContext context, ProjectViewSet projectViewSet) {
    Collection<Sdk> sdks = AndroidSdkUtils.getAllAndroidSdks();
    if (sdks.isEmpty()) {
      IssueOutput.error("No Android SDK configured. Please use the SDK manager to configure.")
          .navigatable(
              new Navigatable() {
                @Override
                public void navigate(boolean b) {
                  SdkUtil.openSdkManager();
                }

                @Override
                public boolean canNavigate() {
                  return true;
                }

                @Override
                public boolean canNavigateToSource() {
                  return false;
                }
              })
          .submit(context);
      return null;
    }
    String androidSdk = null;
    if (projectViewSet != null) {
      androidSdk = projectViewSet.getScalarValue(AndroidSdkPlatformSection.KEY);
    }

    if (androidSdk == null) {
      IssueOutput.error(
              ("No android_sdk_platform set. Please set to an android platform. "
                  + "Available android_sdk_platforms are: "
                  + getAvailableSdkPlatforms(sdks)))
          .inFile(projectViewSet.getTopLevelProjectViewFile().projectViewFile)
          .submit(context);
      return null;
    }

    Sdk sdk = AndroidSdkUtils.findSuitableAndroidSdk(androidSdk);
    if (sdk == null) {
      IssueOutput.error(
              ("No such android_sdk_platform: '"
                  + androidSdk
                  + "'. "
                  + "Available android_sdk_platforms are: "
                  + getAvailableSdkPlatforms(sdks)
                  + ". "
                  + "Please change android_sdk_platform or run SDK manager "
                  + "to download missing SDK platforms."))
          .inFile(projectViewSet.getTopLevelProjectViewFile().projectViewFile)
          .submit(context);
      return null;
    }

    int androidSdkApiLevel = getAndroidSdkApiLevel(sdk);
    return new AndroidSdkPlatform(androidSdk, androidSdkApiLevel);
  }

  public static String getAvailableSdkPlatforms(Collection<Sdk> sdks) {
    List<String> names = Lists.newArrayList();
    for (Sdk sdk : sdks) {
      AndroidSdkAdditionalData additionalData = AndroidSdkUtils.getAndroidSdkAdditionalData(sdk);
      if (additionalData == null) {
        continue;
      }
      String targetHash = additionalData.getBuildTargetHashString();
      names.add(targetHash);
    }
    return "{" + Joiner.on(", ").join(names) + "}";
  }

  private static int getAndroidSdkApiLevel(Sdk sdk) {
    int androidSdkApiLevel = 1;
    AndroidSdkAdditionalData additionalData = (AndroidSdkAdditionalData) sdk.getSdkAdditionalData();
    if (additionalData != null) {
      AndroidPlatform androidPlatform = additionalData.getAndroidPlatform();
      if (androidPlatform != null) {
        androidSdkApiLevel = androidPlatform.getApiLevel();
      }
    }
    return androidSdkApiLevel;
  }
}
