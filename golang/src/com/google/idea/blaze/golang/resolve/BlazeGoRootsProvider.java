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
package com.google.idea.blaze.golang.resolve;

import com.google.idea.blaze.base.io.FileOperationProvider;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.settings.BlazeImportSettings;
import com.google.idea.blaze.base.settings.BlazeImportSettingsManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import java.io.File;
import java.io.IOException;
import javax.annotation.Nullable;

/** Deletes directory of symlinks left behind from #api181. */
public class BlazeGoRootsProvider {
  private static final Logger logger = Logger.getInstance(BlazeGoRootsProvider.class);

  public static void handleGoSymlinks(
      BlazeContext context, Project project, BlazeProjectData projectData) {
    deleteOldGoPath(project);
  }

  @Nullable
  private static File getGoRoot(Project project) {
    BlazeImportSettings importSettings =
        BlazeImportSettingsManager.getInstance(project).getImportSettings();
    return importSettings != null
        ? new File(importSettings.getProjectDataDirectory(), ".gopath")
        : null;
  }

  private static synchronized void deleteOldGoPath(Project project) {
    File goRoot = getGoRoot(project);
    if (goRoot == null) {
      return;
    }
    FileOperationProvider fileOperation = FileOperationProvider.getInstance();
    if (fileOperation.exists(goRoot)) {
      try {
        fileOperation.deleteRecursively(goRoot);
      } catch (IOException e) {
        logger.error(e);
      }
    }
  }
}
