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
package com.google.idea.blaze.base.run.testmap;

import static com.google.idea.common.guava.GuavaHelper.toImmutableList;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.dependencies.TargetInfo;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetMap;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.run.TestTargetFinder;
import com.google.idea.blaze.base.sync.SyncCache;
import com.google.idea.blaze.base.sync.workspace.ArtifactLocationDecoder;
import com.intellij.openapi.project.Project;
import java.io.File;
import java.util.Collection;
import javax.annotation.Nullable;

/**
 * Used to locate tests from source files for things like right-clicks.
 *
 * <p>It's essentially a map from source file -> reachable test rules.
 */
public class TestTargetFilterImpl implements TestTargetFinder {

  private final Project project;

  public TestTargetFilterImpl(Project project) {
    this.project = project;
  }

  @Override
  public Collection<TargetInfo> testTargetsForSourceFile(File sourceFile) {
    FilteredTargetMap testMap =
        SyncCache.getInstance(project)
            .get(TestTargetFilterImpl.class, TestTargetFilterImpl::computeTestMap);
    if (testMap == null) {
      return ImmutableList.of();
    }
    return testMap
        .targetsForSourceFile(sourceFile)
        .stream()
        .map(TargetIdeInfo::toTargetInfo)
        .collect(toImmutableList());
  }

  private static FilteredTargetMap computeTestMap(Project project, BlazeProjectData projectData) {
    return computeTestMap(project, projectData.artifactLocationDecoder, projectData.targetMap);
  }

  @VisibleForTesting
  static FilteredTargetMap computeTestMap(
      Project project, ArtifactLocationDecoder decoder, TargetMap targetMap) {
    return new FilteredTargetMap(project, decoder, targetMap, TestTargetFilterImpl::isTestTarget);
  }

  private static boolean isTestTarget(@Nullable TargetIdeInfo target) {
    return target != null && target.kind != null && Kind.isTestRule(target.kind.toString());
  }
}
