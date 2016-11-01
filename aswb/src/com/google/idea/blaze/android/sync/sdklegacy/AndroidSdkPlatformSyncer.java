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
package com.google.idea.blaze.android.sync.sdklegacy;

import com.android.tools.idea.startup.AndroidStudioInitializer;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.idea.blaze.android.projectview.AndroidSdkPlatformSection;
import com.google.idea.blaze.android.settings.AswbGlobalSettings;
import com.google.idea.blaze.android.sync.model.AndroidSdkPlatform;
import com.google.idea.blaze.base.projectview.ProjectViewManager;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.output.IssueOutput;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.Blaze.BuildSystem;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import java.io.File;
import java.util.List;
import javax.annotation.Nullable;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.android.sdk.AndroidSdkAdditionalData;
import org.jetbrains.android.sdk.AndroidSdkUtils;

/** Calculates AndroidSdkPlatform. */
@Deprecated
public class AndroidSdkPlatformSyncer {
  @Nullable
  public static AndroidSdkPlatform getAndroidSdkPlatform(Project project, BlazeContext context) {

    final String localSdkLocation;
    if (AndroidStudioInitializer.isAndroidSdkManagerEnabled()) {
      Sdk sdk = Iterables.getFirst(AndroidSdkUtils.getAllAndroidSdks(), null);
      if (sdk == null) {
        IssueOutput.error(
                "Error: No Android SDK configured. Please use the SDK manager to configure.")
            .submit(context);
        return null;
      }
      localSdkLocation = sdk.getHomePath();
    } else {
      localSdkLocation = AswbGlobalSettings.getInstance().getLocalSdkLocation();
      if (localSdkLocation == null) {
        IssueOutput.error(
                "Error: No Android SDK synced yet."
                    + (Blaze.defaultBuildSystem() == BuildSystem.Blaze
                        ? " Please sync SDK following go/aswb-sdk."
                        : ""))
            .submit(context);
        return null;
      }
    }

    String androidSdkPlatform = null;
    ProjectViewSet projectViewSet = ProjectViewManager.getInstance(project).getProjectViewSet();
    if (projectViewSet != null) {
      androidSdkPlatform = projectViewSet.getScalarValue(AndroidSdkPlatformSection.KEY);
    }

    // This is verified in the project view verification step, but double-check here
    if (androidSdkPlatform == null) {
      IssueOutput.error(
              "No android_sdk_platform set. Please ensure this is set to a platform SDK directory.")
          .submit(context);
      return null;
    }

    String androidSdk =
        BlazeAndroidSdk.getAndroidSdkLevelFromLocalChannel(localSdkLocation, androidSdkPlatform);

    if (androidSdk == null) {
      IssueOutput.error(
              Joiner.on("\n")
                  .join(
                      "No such android_sdk_platform: " + androidSdkPlatform,
                      "Available android_sdk_platforms are: "
                          + getAvailableSdkPlatforms(localSdkLocation)))
          .inFile(projectViewSet.getTopLevelProjectViewFile().projectViewFile)
          .submit(context);
      return null;
    }

    Sdk sdk = AndroidSdkUtils.findSuitableAndroidSdk(androidSdk);
    if (sdk == null) {
      ImmutableList.Builder<String> error =
          ImmutableList.<String>builder()
              .add(
                  String.format(
                      "Can't find a matching SDK "
                          + "(was looking for '%s' in the '%s' platform directory).",
                      androidSdk, androidSdkPlatform),
                  "Available android_sdk_platforms are: "
                      + getAvailableSdkPlatforms(localSdkLocation));
      if (Blaze.defaultBuildSystem() == BuildSystem.Blaze) {
        error.add(
            "If you have no SDK, please sync your SDK by following go/aswb-sdk and try again. ",
            "If you have done everything correctly, this can be due to an SDK sync manager bug.",
            "To workaround, please delete ~/.AndroidStudioWithBlazeXX/system and restart");
      }

      IssueOutput.error(String.join("\n", error.build())).submit(context);
      return null;
    }

    int androidSdkApiLevel = getAndroidSdkApiLevel(androidSdk);
    return new AndroidSdkPlatform(androidSdk, androidSdkApiLevel);
  }

  private static String getAvailableSdkPlatforms(String localSdkDirectoryString) {
    File localSdkDirectory = new File(localSdkDirectoryString);
    if (localSdkDirectory.exists()) {
      File platformDirectory = new File(localSdkDirectory, "platforms");
      if (platformDirectory.exists()) {
        File[] children = platformDirectory.listFiles();
        if (children != null) {
          List<String> names = Lists.newArrayList();
          for (File child : children) {
            if (child.isDirectory()) {
              names.add('"' + child.getName() + '"');
            }
          }
          return "{" + Joiner.on(", ").join(names) + "}";
        }
      }
    }
    return "<No platforms found>";
  }

  private static int getAndroidSdkApiLevel(String androidSdk) {
    int androidSdkApiLevel = 1;
    Sdk sdk = AndroidSdkUtils.findSuitableAndroidSdk(androidSdk);
    if (sdk != null) {
      AndroidSdkAdditionalData additionalData =
          (AndroidSdkAdditionalData) sdk.getSdkAdditionalData();
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
