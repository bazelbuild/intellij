/*
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.android.run;

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.android.run.binary.mobileinstall.BlazeApkBuildStepMobileInstall;
import com.google.idea.blaze.android.run.runner.BlazeApkBuildStep;
import com.google.idea.blaze.android.run.runner.BlazeApkBuildStepNormalBuild;
import com.google.idea.blaze.base.model.primitives.Label;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;

/** A service that provides APK build steps. */
public interface BlazeApkBuildService {
  static BlazeApkBuildService getInstance(Project project) {
    return ServiceManager.getService(project, BlazeApkBuildService.class);
  }

  /** Returns a build step for the given build configurations. */
  BlazeApkBuildStep getBuildStep(
      boolean useMobileInstall,
      Label label,
      ImmutableList<String> blazeFlags,
      ImmutableList<String> exeFlags);

  /**
   * A default implementation that uses {@link BlazeApkBuildStepNormalBuild} and {@link
   * BlazeApkBuildStepMobileInstall}.
   */
  class DefaultBuildService implements BlazeApkBuildService {
    private final Project project;

    public DefaultBuildService(Project project) {
      this.project = project;
    }

    @Override
    public BlazeApkBuildStep getBuildStep(
        boolean useMobileInstall,
        Label label,
        ImmutableList<String> blazeFlags,
        ImmutableList<String> exeFlags) {
      if (useMobileInstall) {
        return new BlazeApkBuildStepMobileInstall(project, label, blazeFlags, exeFlags);
      } else {
        return new BlazeApkBuildStepNormalBuild(project, label, blazeFlags);
      }
    }
  }
}
