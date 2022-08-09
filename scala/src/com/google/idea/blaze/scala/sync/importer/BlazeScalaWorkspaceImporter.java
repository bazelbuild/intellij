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
package com.google.idea.blaze.scala.sync.importer;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetKey;
import com.google.idea.blaze.base.ideinfo.TargetMap;
import com.google.idea.blaze.base.model.LibraryKey;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.sync.projectview.ProjectViewTargetImportFilter;
import com.google.idea.blaze.base.targetmaps.TransitiveDependencyMap;
import com.google.idea.blaze.java.sync.importer.JavaSourceFilter;
import com.google.idea.blaze.java.sync.model.BlazeJarLibrary;
import com.google.idea.blaze.scala.sync.model.BlazeScalaImportResult;
import com.intellij.openapi.project.Project;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Builds a BlazeWorkspace. */
public final class BlazeScalaWorkspaceImporter {
  private final Project project;
  private final WorkspaceRoot workspaceRoot;
  private final ProjectViewSet projectViewSet;
  private final TargetMap targetMap;

  public BlazeScalaWorkspaceImporter(
      Project project,
      WorkspaceRoot workspaceRoot,
      ProjectViewSet projectViewSet,
      TargetMap targetMap) {
    this.project = project;
    this.workspaceRoot = workspaceRoot;
    this.projectViewSet = projectViewSet;
    this.targetMap = targetMap;
  }

  public BlazeScalaImportResult importWorkspace() {
    ProjectViewTargetImportFilter importFilter =
        new ProjectViewTargetImportFilter(
            Blaze.getBuildSystemName(project), workspaceRoot, projectViewSet);

    List<TargetKey> scalaSourceTargets =
        targetMap.targets().stream()
            .filter(target -> target.getJavaIdeInfo() != null)
            .filter(target -> target.getKind().hasLanguage(LanguageClass.SCALA))
            .filter(importFilter::isSourceTarget)
            .map(TargetIdeInfo::getKey)
            .collect(Collectors.toList());

    Map<LibraryKey, BlazeJarLibrary> libraries = Maps.newHashMap();

    // Add every jar in the transitive closure of dependencies.
    // Direct dependencies of the working set will be double counted by BlazeJavaWorkspaceImporter,
    // but since they'll all merged into one set, we will end up with exactly one of each.
    for (TargetKey dependency :
        TransitiveDependencyMap.getTransitiveDependencies(scalaSourceTargets, targetMap)) {
      TargetIdeInfo target = targetMap.get(dependency);
      if (target == null) {
        continue;
      }
      // Except source targets.
      if (JavaSourceFilter.importAsSource(importFilter, target)) {
        continue;
      }
      if (target.getJavaIdeInfo() != null) {
        target.getJavaIdeInfo().getJars().stream()
            .map(jar -> new BlazeJarLibrary(jar, target.getKey()))
            .forEach(library -> libraries.putIfAbsent(library.key, library));
      }
    }

    return new BlazeScalaImportResult(ImmutableMap.copyOf(libraries));
  }
}
