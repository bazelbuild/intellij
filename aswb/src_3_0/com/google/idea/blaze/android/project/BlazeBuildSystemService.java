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

import com.android.tools.idea.npw.project.AndroidSourceSet;
import com.android.tools.idea.project.BuildSystemService;
import com.google.idea.blaze.android.npw.project.BlazeAndroidProjectPaths;
import com.google.idea.blaze.android.sync.model.AndroidResourceModuleRegistry;
import com.google.idea.blaze.base.actions.BlazeBuildService;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.io.VfsUtils;
import com.google.idea.blaze.base.lang.buildfile.references.BuildReferenceManager;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.sync.BlazeSyncManager;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import java.io.File;
import java.util.List;
import javax.annotation.Nullable;
import org.jetbrains.android.facet.AndroidFacet;

/** Blaze implementation of {@link BuildSystemService} for build system specific operations. */
public class BlazeBuildSystemService extends BuildSystemService {
  @Override
  public boolean isApplicable(Project project) {
    return Blaze.isBlazeProject(project);
  }

  @Override
  public void buildProject(Project project) {
    BlazeBuildService.getInstance().buildProject(project);
  }

  @Override
  public void syncProject(Project project) {
    BlazeSyncManager.getInstance(project).incrementalProjectSync();
  }

  @Override
  public void addDependency(Module module, String artifact) {
    Project project = module.getProject();
    BlazeProjectData blazeProjectData =
        BlazeProjectDataManager.getInstance(project).getBlazeProjectData();
    if (blazeProjectData == null) {
      return;
    }
    AndroidResourceModuleRegistry registry = AndroidResourceModuleRegistry.getInstance(project);
    TargetIdeInfo targetIdeInfo = blazeProjectData.targetMap.get(registry.getTargetKey(module));
    if (targetIdeInfo == null || targetIdeInfo.buildFile == null) {
      return;
    }

    // TODO: automagically edit deps instead of just opening the BUILD file?
    // Need to translate Gradle coordinates into blaze targets.
    // Will probably need to hardcode for each dependency.
    FileEditorManager fileEditorManager = FileEditorManager.getInstance(project);
    PsiElement buildTargetPsi =
        BuildReferenceManager.getInstance(project).resolveLabel(targetIdeInfo.key.label);
    if (buildTargetPsi != null) {
      // If we can find a PSI for the target,
      // then we can jump straight to the target in the build file.
      fileEditorManager.openTextEditor(
          new OpenFileDescriptor(
              project,
              buildTargetPsi.getContainingFile().getVirtualFile(),
              buildTargetPsi.getTextOffset()),
          true);
    } else {
      // If not, just the build file is good enough.
      File buildIoFile = blazeProjectData.artifactLocationDecoder.decode(targetIdeInfo.buildFile);
      VirtualFile buildVirtualFile = VfsUtils.resolveVirtualFile(buildIoFile);
      if (buildVirtualFile != null) {
        fileEditorManager.openFile(buildVirtualFile, true);
      }
    }
  }

  @Override
  public String mergeBuildFiles(
      String dependencies,
      String destinationContents,
      Project project,
      @Nullable String supportLibVersionFilter) {
    // TODO: check if necessary to implement.
    return null;
  }

  @Override
  public List<AndroidSourceSet> getSourceSets(
      AndroidFacet facet, @Nullable VirtualFile targetDirectory) {
    return BlazeAndroidProjectPaths.getSourceSets(facet, targetDirectory);
  }
}
