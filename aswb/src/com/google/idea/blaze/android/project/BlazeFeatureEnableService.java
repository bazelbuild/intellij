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
package com.google.idea.blaze.android.project;

import com.android.tools.idea.project.FeatureEnableService;
import com.google.common.collect.ImmutableMap;
import com.google.idea.blaze.android.settings.BlazeAndroidUserSettings;
import com.google.idea.blaze.base.logging.EventLogger;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.common.experiments.BoolExperiment;
import com.intellij.openapi.project.Project;

/** Enable features supported by the blaze integration. */
public class BlazeFeatureEnableService extends FeatureEnableService {
  private static final EventLogger logger = EventLogger.getInstance();

  private static final BoolExperiment ENABLE_LAYOUT_EDITOR =
      new BoolExperiment("enable.layout.editor", true);

  @Override
  protected boolean isApplicable(Project project) {
    return Blaze.isBlazeProject(project);
  }

  @Override
  public boolean isLayoutEditorEnabled(Project project) {
    boolean isEnabled =
        isLayoutEditorExperimentEnabled()
            && BlazeAndroidUserSettings.getInstance().getUseLayoutEditor();
    boolean isReady = BlazeProjectDataManager.getInstance(project).getBlazeProjectData() != null;
    logger.log(
        getClass(), "layout_editor", ImmutableMap.of("enabled", Boolean.toString(isEnabled)));
    return isEnabled && isReady;
  }

  public static boolean isLayoutEditorExperimentEnabled() {
    return ENABLE_LAYOUT_EDITOR.getValue();
  }
}
