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
package com.google.idea.blaze.base.run.smrunner;

import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.TargetExpression;
import com.google.idea.blaze.base.settings.Blaze.BuildSystem;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import java.util.Arrays;
import java.util.Objects;
import javax.annotation.Nullable;

/** Provides a {@link BlazeTestUiSession} for a given project. */
public interface TestUiSessionProvider {
  ExtensionPointName<TestUiSessionProvider> EP_NAME =
      ExtensionPointName.create("com.google.idea.blaze.TestUiSessionProvider");

  /** Returns a {@link BlazeTestUiSession} for the given project and blaze target. */
  @Nullable
  static BlazeTestUiSession createForTarget(Project project, TargetExpression target) {
    if (!BlazeTestEventsHandler.targetSupported(project, target)) {
      return null;
    }
    BlazeProjectData projectData =
        BlazeProjectDataManager.getInstance(project).getBlazeProjectData();
    if (projectData == null) {
      return null;
    }
    return Arrays.stream(EP_NAME.getExtensions())
        .map(provider -> provider.getTestUiSession(projectData.blazeVersionData.buildSystem()))
        .filter(Objects::nonNull)
        .findFirst()
        .orElse(null);
  }

  /**
   * Returns a {@link BlazeTestUiSession}, or {@code null} if this provider doesn't handle the given
   * project.
   */
  @Nullable
  BlazeTestUiSession getTestUiSession(BuildSystem buildSystem);
}
