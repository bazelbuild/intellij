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
package com.google.idea.blaze.java.fastbuild;

import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.base.model.BlazeVersionData;
import com.google.idea.blaze.base.settings.BuildSystemName;
import com.google.idea.blaze.base.sync.aspects.strategy.AspectRepositoryProvider;
import com.intellij.openapi.project.Project;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

final class BazelFastBuildAspectStrategy extends FastBuildAspectStrategy {

  @Override
  protected List<String> getAspectFlags(BlazeVersionData versionData, Project project) {
    String intellijAspectFile;
    boolean useInjectedRepository = versionData.bazelIsAtLeastVersion(8, 0, 0);
    if(useInjectedRepository) {
      intellijAspectFile = "--aspects=@intellij_aspect//:fast_build_info_bundled.bzl%fast_build_info_aspect";
    } else if (versionData.bazelIsAtLeastVersion(6, 0, 0)) {
      intellijAspectFile = "--aspects=@@intellij_aspect//:fast_build_info_bundled.bzl%fast_build_info_aspect";
    } else {
      intellijAspectFile = "--aspects=@intellij_aspect//:fast_build_info_bundled.bzl%fast_build_info_aspect";
    }
    return Stream.concat(
        getAspectRepositoryOverrideFlags(project).stream(),
        Stream.of(intellijAspectFile)
      )
      .collect(Collectors.toList());
  }

  private static List<String> getAspectRepositoryOverrideFlags(Project project) {
    return Arrays.stream(AspectRepositoryProvider.getOverrideFlags(project)).filter(Optional::isPresent)
      .map(Optional::get).toList();
  }

  @Override
  public ImmutableSet<BuildSystemName> getSupportedBuildSystems() {
    return ImmutableSet.of(BuildSystemName.Bazel);
  }
}
