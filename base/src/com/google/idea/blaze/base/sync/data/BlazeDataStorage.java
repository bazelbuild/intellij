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
package com.google.idea.blaze.base.sync.data;

import com.google.common.base.Strings;
import com.google.idea.blaze.base.settings.BlazeImportSettings;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.project.Project;
import java.io.File;

/** Defines where we store our blaze project data. */
public class BlazeDataStorage {
  public static final String WORKSPACE_MODULE_NAME = ".workspace";
  public static final String BLAZE_DATA_SUBDIRECTORY = ".blaze";
  public static final String PROJECT_DATA_SUBDIRECTORY = getProjectDataSubdirectory();

  private static String getProjectDataSubdirectory() {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      return ".ijwb";
    }
    switch (ApplicationInfo.getInstance().getBuild().getProductCode()) {
      case "CL": // CLion
        return ".clwb";
      case "AI": // Android Studio
        return ".aswb";
      default:
        return ".ijwb";
    }
  }

  public static File getProjectDataDir(BlazeImportSettings importSettings) {
    return new File(importSettings.getProjectDataDirectory(), BLAZE_DATA_SUBDIRECTORY);
  }

  public static File getProjectCacheDir(Project project, BlazeImportSettings importSettings) {
    String locationHash = importSettings.getLocationHash();

    // Legacy support: The location hash used to be just the project hash
    if (Strings.isNullOrEmpty(locationHash)) {
      locationHash = project.getLocationHash();
    }

    return new File(getProjectConfigurationDir(), locationHash);
  }

  private static File getProjectConfigurationDir() {
    return new File(PathManager.getSystemPath(), "blaze/projects").getAbsoluteFile();
  }
}
