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
package com.google.idea.blaze.cpp;

import com.google.common.base.Preconditions;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.sdkcompat.cidr.CidrStartupActivitiesToSuppress;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.extensions.ExtensionPoint;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.extensions.LoadingOrder;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;

/**
 * cidr-lang usually registers some StartupActivity instances that get run on project open to
 * rebuild symbols. This causes problems with blaze projects because this happens before the project
 * configuration has been set up. CLwB/ASwB trigger a symbol rebuild after the startup sync.
 */
public class CidrSymbolBuilderSuppressor implements ApplicationComponent {
  private void addFiltersToStartupActivities() {
    ExtensionPoint<StartupActivity> ep =
        Extensions.getRootArea().getExtensionPoint(StartupActivity.POST_STARTUP_ACTIVITY);
    for (Class<? extends StartupActivity> startupActivity :
        CidrStartupActivitiesToSuppress.STARTUP_ACTIVITIES_TO_SUPPRESS) {
      StartupActivity startupActivityInstance =
          StartupActivity.POST_STARTUP_ACTIVITY.findExtension(startupActivity);
      Preconditions.checkNotNull(startupActivityInstance);
      StartupActivity replacementStartupActivity =
          new BlazeSuppressStartupActivity(startupActivityInstance);
      ep.registerExtension(
          replacementStartupActivity, LoadingOrder.before(startupActivity.getSimpleName()));
      ep.unregisterExtension(startupActivityInstance);
    }
  }

  @Override
  public void initComponent() {
    addFiltersToStartupActivities();
  }

  @Override
  public void disposeComponent() {}

  @Override
  public String getComponentName() {
    return "CidrSymbolBuilderSuppressor";
  }

  private static class BlazeSuppressStartupActivity implements StartupActivity {
    final StartupActivity original;

    private BlazeSuppressStartupActivity(StartupActivity original) {
      this.original = original;
    }

    @Override
    public void runActivity(Project project) {
      if (!Blaze.isBlazeProject(project)) {
        original.runActivity(project);
      }
    }
  }
}
