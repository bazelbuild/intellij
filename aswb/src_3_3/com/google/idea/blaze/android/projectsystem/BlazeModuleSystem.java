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

import com.android.ide.common.repository.GradleCoordinate;
import com.android.ide.common.util.PathString;
import com.android.projectmodel.JavaLibrary;
import com.android.projectmodel.Library;
import com.android.tools.idea.projectsystem.AndroidModuleSystem;
import com.android.tools.idea.projectsystem.CapabilityNotSupported;
import com.android.tools.idea.projectsystem.CapabilityStatus;
import com.android.tools.idea.projectsystem.DependencyManagementException;
import com.android.tools.idea.projectsystem.NamedModuleTemplate;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.android.npw.project.BlazeAndroidModuleTemplate;
import com.google.idea.blaze.android.sync.model.AndroidResourceModuleRegistry;
import com.google.idea.blaze.base.ideinfo.AndroidIdeInfo;
import com.google.idea.blaze.base.ideinfo.JavaIdeInfo;
import com.google.idea.blaze.base.ideinfo.LibraryArtifact;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetKey;
import com.google.idea.blaze.base.ideinfo.TargetMap;
import com.google.idea.blaze.base.io.VfsUtils;
import com.google.idea.blaze.base.lang.buildfile.references.BuildReferenceManager;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.base.sync.workspace.ArtifactLocationDecoder;
import com.google.idea.blaze.base.targetmaps.TransitiveDependencyMap;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nullable;

/** Blaze implementation of {@link AndroidModuleSystem} */
public class BlazeModuleSystem implements AndroidModuleSystem {
  private Module module;

  public BlazeModuleSystem(Module module) {
    this.module = module;
  }

  @Override
  public CapabilityStatus canGeneratePngFromVectorGraphics() {
    // We're currently unsure of the state of the Blaze support, so we report that it's unsupported.
    // TODO: Change this to "supported" when and if we can confirm that Blaze supports it
    return new CapabilityNotSupported(
        "<html><p>Blaze does not support generation of PNG images from vector assets. "
            + "Vector asset support requires a SDK version of at least 21.</p></html>",
        "Vector Assets Not Supported");
  }

  @Override
  public CapabilityStatus getInstantRunSupport() {
    return new CapabilityNotSupported();
  }

  @Override
  public List<NamedModuleTemplate> getModuleTemplates(
      @javax.annotation.Nullable VirtualFile targetDirectory) {
    return BlazeAndroidModuleTemplate.getTemplates(module, targetDirectory);
  }

  @Override
  public void registerDependency(GradleCoordinate coordinate) {
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

  @Nullable
  @Override
  public GradleCoordinate getRegisteredDependency(GradleCoordinate coordinate)
      throws DependencyManagementException {
    return null;
  }

  @Nullable
  @Override
  public GradleCoordinate getResolvedDependency(GradleCoordinate coordinate)
      throws DependencyManagementException {
    // External dependencies can be imported into the project via many routes (e.g. maven_jar,
    // local_repository,
    // custom repo paths, etc). Within the project these dependencies are all referenced by their
    // TargetKey.
    // Here we use a locator to convert coordinates to TargetKey labels in order to find them.

    BlazeProjectData projectData =
        BlazeProjectDataManager.getInstance(module.getProject()).getBlazeProjectData();

    if (projectData == null) {
      return null;
    }

    boolean projectHasDependency =
        MavenArtifactLocator.forBuildSystem(Blaze.getBuildSystem(module.getProject()))
            .stream()
            .map(locator -> locator.labelFor(coordinate))
            .filter(Objects::nonNull)
            .anyMatch(label -> projectData.targetMap.contains(TargetKey.forPlainTarget(label)));

    return projectHasDependency ? coordinate : null;
  }

  @Override
  public Collection<Library> getDependentLibraries() {
    Project project = module.getProject();
    BlazeProjectData blazeProjectData =
        BlazeProjectDataManager.getInstance(project).getBlazeProjectData();

    if (blazeProjectData == null) {
      return ImmutableList.of();
    }

    TargetMap targetMap = blazeProjectData.targetMap;
    ArtifactLocationDecoder decoder = blazeProjectData.artifactLocationDecoder;

    AndroidResourceModuleRegistry registry = AndroidResourceModuleRegistry.getInstance(project);
    TargetIdeInfo target = targetMap.get(registry.getTargetKey(module));
    if (target == null) {
      // TODO: why are we only returning dependencies for resource modules?
      // TODO: maybe we should see if module is named BlazeDataStorage.WORKSPACE_MODULE_NAME
      return ImmutableList.of();
    }

    ImmutableList.Builder<Library> libraries = ImmutableList.builder();
    for (TargetKey dependencyTargetKey :
        TransitiveDependencyMap.getInstance(project).getTransitiveDependencies(target.key)) {
      TargetIdeInfo dependencyTarget = targetMap.get(dependencyTargetKey);
      if (dependencyTarget == null) {
        continue;
      }

      // Add all import jars as external libraries.
      JavaIdeInfo javaIdeInfo = dependencyTarget.javaIdeInfo;
      if (javaIdeInfo != null) {
        int i = 0;
        for (LibraryArtifact jar : javaIdeInfo.jars) {
          if (jar.classJar != null) {
            String address = dependencyTarget.key.toString() + "-" + Integer.toString(i++);
            PathString classJar = new PathString(decoder.decode(jar.classJar));
            libraries.add(new JavaLibrary(address, classJar));
          }
        }
      }

      // Add all android resource targets as external aar libraries
      AndroidIdeInfo androidIdeInfo = dependencyTarget.androidIdeInfo;
      if (androidIdeInfo != null && !Strings.isNullOrEmpty(androidIdeInfo.resourceJavaPackage)) {
        // TODO(b/110210936): once we start constructing aars, we need to supply
        // the right aars here
      }
    }

    return libraries.build();
  }
}
