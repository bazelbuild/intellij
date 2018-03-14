/*
 * Copyright 2018 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.kotlin.sync.importer;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetMap;
import com.google.idea.blaze.base.model.LibraryKey;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.sync.projectview.ProjectViewTargetImportFilter;
import com.google.idea.blaze.base.targetmaps.TransitiveDependencyMap;
import com.google.idea.blaze.java.sync.model.BlazeJarLibrary;
import com.google.idea.blaze.kotlin.sync.model.BlazeKotlinImportResult;
import com.intellij.openapi.project.Project;
import java.util.HashMap;
import java.util.Objects;
import java.util.stream.Stream;

/** Computes Kotlin-specific project sync data. */
public class BlazeKotlinWorkspaceImporter {
  private final TargetMap targetMap;
  private final ProjectViewTargetImportFilter importFilter;

  public BlazeKotlinWorkspaceImporter(
      Project project,
      WorkspaceRoot workspaceRoot,
      ProjectViewSet projectViewSet,
      TargetMap targetMap) {
    this.targetMap = targetMap;
    importFilter = new ProjectViewTargetImportFilter(project, workspaceRoot, projectViewSet);
  }

  public BlazeKotlinImportResult importWorkspace() {
    HashMap<LibraryKey, BlazeJarLibrary> libraries = new HashMap<>();
    HashMap<TargetIdeInfo, ImmutableList<BlazeJarLibrary>> targetLibraryMap = new HashMap<>();

    collectTransitiveLibsFromKotlinSourceTargets(libraries, targetLibraryMap);

    return new BlazeKotlinImportResult(
        ImmutableList.copyOf(libraries.values()), ImmutableMap.copyOf(targetLibraryMap));
  }

  private void collectTransitiveLibsFromKotlinSourceTargets(
      HashMap<LibraryKey, BlazeJarLibrary> libraries,
      HashMap<TargetIdeInfo, ImmutableList<BlazeJarLibrary>> targetLibraryMap) {
    transitiveKotlinTargets()
        .forEach(
            depIdeInfo -> {
              // noinspection ConstantConditions
              BlazeJarLibrary[] transitiveLibraries =
                  depIdeInfo
                      .javaIdeInfo
                      .jars
                      .stream()
                      .map(BlazeJarLibrary::new)
                      .peek(depJar -> libraries.putIfAbsent(depJar.key, depJar))
                      .toArray(BlazeJarLibrary[]::new);
              targetLibraryMap.put(depIdeInfo, ImmutableList.copyOf(transitiveLibraries));
            });
  }

  private Stream<TargetIdeInfo> transitiveKotlinTargets() {
    return targetMap
        .targets()
        .stream()
        .filter(target -> target.kind.languageClass.equals(LanguageClass.KOTLIN))
        .filter(importFilter::isSourceTarget)
        .flatMap(this::expandWithKotlinTargets);
  }

  private Stream<TargetIdeInfo> expandWithKotlinTargets(TargetIdeInfo target) {
    return Stream.concat(
        Stream.of(target),
        // all transitive targets with a java ide info that are also kotlin providers.
        TransitiveDependencyMap.getTransitiveDependencies(target.key, targetMap)
            .stream()
            .map(targetMap::get)
            .filter(Objects::nonNull)
            .filter(info -> info.javaIdeInfo != null)
            .filter(depIdeInfo -> depIdeInfo.kind.languageClass == LanguageClass.KOTLIN));
  }
}
