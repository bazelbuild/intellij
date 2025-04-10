/*
 * Copyright 2020 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.java.libraries;

import com.google.idea.blaze.base.project.startup.ProjectActivityJavaShim;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.BlazeImportSettings.ProjectType;
import com.google.idea.common.experiments.BoolExperiment;
import com.intellij.codeInsight.daemon.impl.LibrarySourceNotificationProvider;
import com.intellij.openapi.project.Project;
import com.intellij.ui.EditorNotificationProvider;
import org.jetbrains.annotations.NotNull;

/**
 * Attempt to disable 'library source doesn't match bytecode' butter bar warnings, which are
 * expected for blaze projects, since we're attaching header jars to the project to improve
 * performance.
 */
final class DisableLibraryBytecodeNotification extends ProjectActivityJavaShim {

  private static final BoolExperiment enabled =
      new BoolExperiment("disable.bytecode.notification", true);

  @Override
  public void runActivity(@NotNull Project project) {
    if (!enabled.getValue() || Blaze.getProjectType(project) == ProjectType.UNKNOWN) {
      return;
    }
    EditorNotificationProvider.EP_NAME.getPoint(project)
        .unregisterExtension(LibrarySourceNotificationProvider.class);
  }
}
