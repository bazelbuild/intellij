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


import com.android.tools.idea.model.ClassJarProvider;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.android.sync.model.AndroidResourceModuleRegistry;
import com.google.idea.blaze.base.command.buildresult.OutputArtifactResolver;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.ideinfo.JavaIdeInfo;
import com.google.idea.blaze.base.ideinfo.LibraryArtifact;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetKey;
import com.google.idea.blaze.base.ideinfo.TargetMap;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.base.sync.workspace.ArtifactLocationDecoder;
import com.google.idea.blaze.base.targetmaps.TransitiveDependencyMap;
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
          ArtifactLocation classJar = jar.getClassJar();
          if (classJar != null && classJar.isSource()) {
            results.add(
                Preconditions.checkNotNull(
                    OutputArtifactResolver.resolve(project, decoder, classJar),
                    "Fail to find file %s",
                    classJar.getRelativePath()));
          }
        }
      }
    }

    return results.build();
  }
}
