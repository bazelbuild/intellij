/*
 * Copyright 2023 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.qsync.java;

import static java.util.function.Predicate.not;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.idea.blaze.qsync.deps.JavaArtifactInfo;
import com.google.idea.blaze.qsync.project.ProjectProto.Project;

/**
 * Updates the project proto with the android resources packages extracted by the aspect in a
 * dependencies build.
 */
public class AndroidResPackagesProjectUpdater {

  private final Project project;
  private final ImmutableList<JavaArtifactInfo> javaArtifacts;

  public AndroidResPackagesProjectUpdater(
      Project project, Iterable<JavaArtifactInfo> javaArtifacts) {
    this.project = project;
    this.javaArtifacts = ImmutableList.copyOf(javaArtifacts);
  }

  public Project addAndroidResPackages() {
    ImmutableList<String> packages =
        javaArtifacts.stream()
            .map(JavaArtifactInfo::androidResourcesPackage)
            .filter(not(Strings::isNullOrEmpty))
            .distinct()
            .collect(ImmutableList.toImmutableList());
    if (packages.isEmpty()) {
      return project;
    }
    return project.toBuilder()
        .setModules(
            0,
            Iterables.getOnlyElement(project.getModulesList()).toBuilder()
                .addAllAndroidSourcePackages(packages)
                .build())
        .build();
  }
}
