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
package com.google.idea.blaze.android.run.binary;

import com.android.ddmlib.IDevice;
import com.android.tools.idea.run.activity.ActivityLocator;
import com.android.tools.idea.run.activity.DefaultActivityLocator;
import com.google.idea.blaze.android.manifest.ManifestParser;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import java.io.File;
import org.jetbrains.android.dom.manifest.Manifest;
import org.jetbrains.annotations.NotNull;

/**
 * An activity launcher which extracts the default launch activity from a generated APK and starts
 * it.
 */
public class BlazeDefaultActivityLocator extends ActivityLocator {
  private final Project project;
  private final File mergedManifestFile;

  public BlazeDefaultActivityLocator(Project project, File mergedManifestFile) {
    this.project = project;
    this.mergedManifestFile = mergedManifestFile;
  }

  @Override
  public void validate() throws ActivityLocatorException {}

  @NotNull
  @Override
  public String getQualifiedActivityName(@NotNull IDevice device) throws ActivityLocatorException {
    Manifest manifest = ManifestParser.getInstance(project).getManifest(mergedManifestFile);
    if (manifest == null) {
      throw new ActivityLocatorException("Could not locate merged manifest");
    }
    String activityName =
        ApplicationManager.getApplication()
            .runReadAction(
                (Computable<String>)
                    () -> DefaultActivityLocator.getDefaultLauncherActivityName(project, manifest));
    if (activityName == null) {
      throw new ActivityLocatorException("Could not locate default activity to launch.");
    }
    return activityName;
  }
}
