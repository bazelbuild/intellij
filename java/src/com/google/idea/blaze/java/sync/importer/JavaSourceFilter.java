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
package com.google.idea.blaze.java.sync.importer;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetKey;
import com.google.idea.blaze.base.ideinfo.TargetMap;
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.sync.projectview.ProjectViewTargetImportFilter;
import com.intellij.openapi.project.Project;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Segments java rules into source/libraries */
public class JavaSourceFilter {
  final List<TargetIdeInfo> sourceTargets;
  final List<TargetIdeInfo> libraryTargets;
  final List<TargetIdeInfo> protoLibraries;
  final Map<TargetKey, Collection<ArtifactLocation>> targetToJavaSources;

  public JavaSourceFilter(
      Project project,
      WorkspaceRoot workspaceRoot,
      ProjectViewSet projectViewSet,
      TargetMap targetMap) {
    ProjectViewTargetImportFilter importFilter =
        new ProjectViewTargetImportFilter(project, workspaceRoot, projectViewSet);
    List<TargetIdeInfo> includedTargets =
        targetMap
            .targets()
            .stream()
            .filter(target -> !importFilter.excludeTarget(target))
            .collect(Collectors.toList());

    List<TargetIdeInfo> javaTargets =
        includedTargets
            .stream()
            .filter(target -> target.javaIdeInfo != null)
            .collect(Collectors.toList());

    targetToJavaSources = Maps.newHashMap();
    for (TargetIdeInfo target : javaTargets) {
      List<ArtifactLocation> javaSources =
          target
              .sources
              .stream()
              .filter(source -> source.getRelativePath().endsWith(".java"))
              .collect(Collectors.toList());
      targetToJavaSources.put(target.key, javaSources);
    }

    sourceTargets = Lists.newArrayList();
    libraryTargets = Lists.newArrayList();
    for (TargetIdeInfo target : javaTargets) {
      boolean importAsSource =
          importFilter.isSourceTarget(target)
              && canImportAsSource(target)
              && (anyNonGeneratedSources(targetToJavaSources.get(target.key)));

      if (importAsSource) {
        sourceTargets.add(target);
      } else {
        libraryTargets.add(target);
      }
    }

    protoLibraries =
        includedTargets
            .stream()
            .filter(target -> target.kind == Kind.PROTO_LIBRARY)
            .collect(Collectors.toList());
  }

  public Iterable<TargetIdeInfo> getSourceTargets() {
    return sourceTargets;
  }

  private boolean canImportAsSource(TargetIdeInfo target) {
    return !target.kindIsOneOf(Kind.JAVA_WRAP_CC, Kind.JAVA_IMPORT);
  }

  private boolean anyNonGeneratedSources(Collection<ArtifactLocation> sources) {
    return sources.stream().anyMatch(ArtifactLocation::isSource);
  }
}
