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
package com.google.idea.blaze.android.projectsystem;

import com.android.tools.apk.analyzer.AaptInvoker;
import com.android.tools.idea.log.LogWrapper;
import com.android.tools.idea.projectsystem.AndroidModuleSystem;
import com.android.tools.idea.projectsystem.AndroidProjectSystem;
import com.android.tools.idea.projectsystem.LightResourceClassService;
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager;
import com.android.tools.idea.res.AndroidInnerClassFinder;
import com.android.tools.idea.res.AndroidResourceClassPsiElementFinder;
import com.android.tools.idea.sdk.AndroidSdks;
import com.google.idea.blaze.android.resources.BlazeLightResourceClassService;
import com.google.idea.blaze.base.actions.BlazeBuildService;
import com.google.idea.common.experiments.BoolExperiment;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElementFinder;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Blaze Implementation of {@link AndroidProjectSystem}. */
public class BlazeProjectSystem implements AndroidProjectSystem {
  /**
   * R classes are generated on the fly by {@link LightResourceClassService}. So there is no need to
   * then augment those R classes. We keep this as an experiment in case something untoward happens,
   * but it can be deleted (b/78110212)
   */
  private static final BoolExperiment ENABLE_PSI_AUGMENT =
      new BoolExperiment("blaze.psi.augment", false);

  private final Project project;
  private final ProjectSystemSyncManager syncManager;
  private final List<PsiElementFinder> myFinders;

  public BlazeProjectSystem(Project project) {
    this.project = project;
    syncManager = new BlazeProjectSystemSyncManager(project);

    myFinders =
        Arrays.asList(
            AndroidInnerClassFinder.INSTANCE,
            new AndroidResourceClassPsiElementFinder(getLightResourceClassService()));
  }

  @Override
  public boolean allowsFileCreation() {
    return true;
  }

  @Nullable
  @Override
  public VirtualFile getDefaultApkFile() {
    return null;
  }

  @Override
  public Path getPathToAapt() {
    return AaptInvoker.getPathToAapt(
        AndroidSdks.getInstance().tryToChooseSdkHandler(),
        new LogWrapper(BlazeProjectSystem.class));
  }

  @Override
  public void buildProject() {
    BlazeBuildService.getInstance().buildProject(project);
  }

  @Override
  public String mergeBuildFiles(
      String dependencies, String destinationContents, @Nullable String supportLibVersionFilter) {
    // TODO: check if necessary to implement.
    return "";
  }

  @Override
  public boolean upgradeProjectToSupportInstantRun() {
    return false;
  }

  @Override
  public AndroidModuleSystem getModuleSystem(Module module) {
    return BlazeModuleSystem.getInstance(module);
  }

  @Override
  public ProjectSystemSyncManager getSyncManager() {
    return syncManager;
  }

  @Nonnull
  @Override
  public Collection<PsiElementFinder> getPsiElementFinders() {
    return myFinders;
  }

  @Override
  public boolean getAugmentRClasses() {
    return ENABLE_PSI_AUGMENT.getValue();
  }

  @Nonnull
  @Override
  public BlazeLightResourceClassService getLightResourceClassService() {
    return BlazeLightResourceClassService.getInstance(project);
  }
}
