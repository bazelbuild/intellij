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
package com.google.idea.blaze.android.sync.model.idea;

import static com.android.tools.idea.projectsystem.ProjectSystemUtil.getModuleSystem;

import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.tools.idea.model.ClassJarProvider;
import com.android.tools.idea.res.ResourceClassRegistry;
import com.android.tools.idea.res.ResourceIdManager;
import com.android.tools.idea.res.ResourceRepositoryManager;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.android.projectsystem.BlazeClassFileFinder;
import com.google.idea.blaze.android.sync.model.AndroidResourceModuleRegistry;
import com.google.idea.blaze.base.ideinfo.AndroidIdeInfo;
import com.google.idea.blaze.base.ideinfo.JavaIdeInfo;
import com.google.idea.blaze.base.ideinfo.LibraryArtifact;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetKey;
import com.google.idea.blaze.base.ideinfo.TargetMap;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.base.sync.workspace.ArtifactLocationDecoder;
import com.google.idea.blaze.base.targetmaps.TransitiveDependencyMap;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import java.io.File;
import java.util.List;

/** Collects class jars from the user's build. */
public class BlazeClassJarProvider implements ClassJarProvider {
  private final Project project;

  public BlazeClassJarProvider(final Project project) {
    this.project = project;
  }

  @Override
  public List<File> getModuleExternalLibraries(Module module) {
    BlazeProjectData blazeProjectData =
        BlazeProjectDataManager.getInstance(project).getBlazeProjectData();

    if (blazeProjectData == null) {
      return ImmutableList.of();
    }

    TargetMap targetMap = blazeProjectData.getTargetMap();
    ArtifactLocationDecoder decoder = blazeProjectData.getArtifactLocationDecoder();

    AndroidResourceModuleRegistry registry = AndroidResourceModuleRegistry.getInstance(project);
    TargetIdeInfo target = targetMap.get(registry.getTargetKey(module));

    if (target == null) {
      return ImmutableList.of();
    }

    boolean skipResourceRegistration =
        ((BlazeClassFileFinder) getModuleSystem(module)).shouldSkipResourceRegistration();

    ImmutableList.Builder<File> results = ImmutableList.builder();
    for (TargetKey dependencyTargetKey :
        TransitiveDependencyMap.getInstance(project).getTransitiveDependencies(target.getKey())) {
      TargetIdeInfo dependencyTarget = targetMap.get(dependencyTargetKey);
      if (dependencyTarget == null) {
        continue;
      }

      // Add all import jars as external libraries.
      JavaIdeInfo javaIdeInfo = dependencyTarget.getJavaIdeInfo();
      if (javaIdeInfo != null) {
        for (LibraryArtifact jar : javaIdeInfo.getJars()) {
          if (jar.getClassJar() != null && jar.getClassJar().isSource()) {
            results.add(decoder.decode(jar.getClassJar()));
          }
        }
      }

      if (skipResourceRegistration) {
        continue;
      }

      // Tell ResourceClassRegistry which repository contains our resources and the java packages of
      // the resources that we're interested in.
      // When the class loader tries to load a custom view, and the view references resource
      // classes, layoutlib will ask the class loader for these resource classes.
      // If these resource classes are in a separate jar from the target (i.e., in a dependency),
      // then offering their jars will lead to a conflict in the resource IDs.
      // So instead, the resource class generator will produce dummy resource classes with
      // non-conflicting IDs to satisfy the class loader.
      // The resource repository remembers the dynamic IDs that it handed out and when the layoutlib
      // calls to ask about the name and content of a given resource ID, the repository can just
      // answer what it has already stored.
      AndroidIdeInfo androidIdeInfo = dependencyTarget.getAndroidIdeInfo();

      ResourceRepositoryManager repositoryManager =
          ResourceRepositoryManager.getOrCreateInstance(module);

      ResourceIdManager idManager = ResourceIdManager.get(module);
      if (androidIdeInfo != null
          && !Strings.isNullOrEmpty(androidIdeInfo.getResourceJavaPackage())
          && repositoryManager != null) {
        // TODO(namespaces)
        ResourceNamespace namespace = ReadAction.compute(repositoryManager::getNamespace);

        ResourceClassRegistry.get(module.getProject())
            .addLibrary(
                repositoryManager.getAppResources(true),
                idManager,
                androidIdeInfo.getResourceJavaPackage(),
                namespace);
      }
    }

    return results.build();
  }
}
