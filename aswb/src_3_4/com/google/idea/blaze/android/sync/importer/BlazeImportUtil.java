/*
 * Copyright 2018 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.android.sync.importer;

import com.android.ide.common.util.PathHashMapKt;
import com.android.ide.common.util.PathMap;
import com.android.ide.common.util.PathString;
import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.android.projectview.GeneratedAndroidResourcesSection;
import com.google.idea.blaze.base.ideinfo.AndroidIdeInfo;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.ideinfo.JavaToolchainIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetMap;
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.Output;
import com.google.idea.blaze.base.scope.output.IssueOutput;
import com.google.idea.blaze.base.sync.projectview.ImportRoots;
import com.google.idea.blaze.base.sync.projectview.ProjectViewTargetImportFilter;
import com.google.idea.blaze.java.sync.model.BlazeContentEntry;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jetbrains.annotations.Nullable;

/** Static utility methods used for blaze import. */
public class BlazeImportUtil {

  public static Collection<ArtifactLocation> resourcesFor(TargetIdeInfo target) {
    AndroidIdeInfo ideInfo = target.getAndroidIdeInfo();
    if (ideInfo != null) {
      return ideInfo.getResources();
    }
    return Collections.emptyList();
  }

  @Nullable
  public static String javaResourcePackageFor(TargetIdeInfo target) {
    AndroidIdeInfo ideInfo = target.getAndroidIdeInfo();
    if (ideInfo != null) {
      return ideInfo.getResourceJavaPackage();
    }
    return null;
  }

  static Consumer<Output> asConsumer(BlazeContext context) {
    return (Output issue) -> {
      context.output(issue);
      if (issue instanceof IssueOutput) {
        IssueOutput issueOutput = (IssueOutput) issue;
        if (issueOutput.getCategory()
            == com.google.idea.blaze.base.scope.output.IssueOutput.Category.ERROR) {
          context.setHasError();
        }
      }
    };
  }

  /**
   * Returns the stream of {@link TargetIdeInfo} corresponding to targets that should be considered
   * source targets in the given {@link TargetMap}.
   */
  static Stream<TargetIdeInfo> getSourceTargetsStream(
      TargetMap targetMap, ProjectViewTargetImportFilter importFilter) {
    return targetMap.targets().stream()
        .filter(target -> target.getKind().languageClass == LanguageClass.ANDROID)
        .filter(target -> target.getAndroidIdeInfo() != null)
        .filter(importFilter::isSourceTarget)
        .filter(target -> !importFilter.excludeTarget(target));
  }

  /**
   * Returns the stream of {@link TargetIdeInfo} corresponding to source targets in the given {@link
   * BlazeImportInput}.
   */
  public static Stream<TargetIdeInfo> getSourceTargetsStream(BlazeImportInput input) {
    return getSourceTargetsStream(input.targetMap, input.createImportFilter());
  }

  /** Returns the source targets for the given {@link BlazeImportInput} as a {@link List}. */
  public static List<TargetIdeInfo> getSourceTargets(BlazeImportInput input) {
    return getSourceTargetsStream(input).collect(Collectors.toList());
  }

  /** Returns the javac jar if it can be found in the given list of targets, otherwise null. */
  static ArtifactLocation getJavacJar(Collection<TargetIdeInfo> targets) {
    return targets.stream()
        .filter(target -> target.getKind() == Kind.JAVA_TOOLCHAIN)
        .map(TargetIdeInfo::getJavaToolchainIdeInfo)
        .filter(Objects::nonNull)
        .map(JavaToolchainIdeInfo::getJavacJar)
        .filter(Objects::nonNull)
        .findFirst()
        .orElse(null);
  }

  /** Returns the set of relative generated resource paths for the given {@link ProjectViewSet}. */
  public static ImmutableSet<String> getWhitelistedGenResourcePaths(ProjectViewSet projectViewSet) {
    return ImmutableSet.copyOf(
        projectViewSet.listItems(GeneratedAndroidResourcesSection.KEY).stream()
            .map(genfilesPath -> genfilesPath.relativePath)
            .collect(Collectors.toSet()));
  }

  /**
   * Returns a predicate that returns true if a fake AAR should be created for the given resource
   * folder. That is, it returns true if fake aars are enabled and the folder is outside the project
   * view.
   */
  public static Predicate<ArtifactLocation> getShouldCreateFakeAarFilter(BlazeImportInput input) {
    if (!input.createFakeAarLibrariesExperiment) {
      return (it) -> Boolean.FALSE;
    }

    ImportRoots importRoots =
        ImportRoots.builder(input.workspaceRoot, input.buildSystem)
            .add(input.projectViewSet)
            .build();
    return artifactLocation ->
        !importRoots.containsWorkspacePath(new WorkspacePath(artifactLocation.getRelativePath()));
  }

  /** Returns a map of PathString onto the most specific BlazeContentEntry for that path. */
  public static PathMap<BlazeContentEntry> getContentEntryForPath(
      Collection<BlazeContentEntry> directories) {
    Map<PathString, BlazeContentEntry> originalMap = new HashMap<>();
    for (BlazeContentEntry next : directories) {
      originalMap.put(new PathString(next.contentRoot), next);
    }
    return PathHashMapKt.toPathMap(originalMap);
  }
}
