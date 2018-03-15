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
package com.google.idea.blaze.android.project;

import com.android.tools.idea.project.FeatureEnableService;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.intellij.openapi.project.Project;

/**
 * Enable features supported by the blaze integration.
 *
 * <p>TODO: remove for Android Studio 3.1.
 */
public class BlazeFeatureEnableService extends FeatureEnableService {
  @Override
  protected boolean isApplicable(Project project) {
    return Blaze.isBlazeProject(project);
  }

  /** Layout edit preview (but not design view) still depends on this in 3.0. */
  @Override
  public boolean isLayoutEditorEnabled(Project project) {
    return BlazeProjectDataManager.getInstance(project).getBlazeProjectData() != null;
  }
}
