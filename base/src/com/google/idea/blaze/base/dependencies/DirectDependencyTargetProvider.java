/*
 * Copyright 2019 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.dependencies;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.run.targetfinder.FuturesUtil;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

/** Provides the blaze targets that are direct dependencies for a given target. */
public interface DirectDependencyTargetProvider {

  ExtensionPointName<DirectDependencyTargetProvider> EP_NAME =
      ExtensionPointName.create("com.google.idea.blaze.DirectDependencyTargetProvider");

  /**
   * Returns the blaze targets provided by the first available {@link
   * DirectDependencyTargetProvider} able to handle the given target, prioritizing any which are
   * immediately available.
   *
   * <p>Future returns null if no provider was able to handle the given target.
   */
  static ListenableFuture<List<TargetInfo>> findDirectDependencyTargets(
      Project project, Label target) {
    Iterable<Future<List<TargetInfo>>> futures =
        Arrays.stream(DirectDependencyTargetProvider.EP_NAME.getExtensions())
            .map(f -> f.getDirectDependencyTargets(project, target))
            .collect(Collectors.toList());
    ListenableFuture<List<TargetInfo>> future =
        FuturesUtil.getFirstFutureSatisfyingPredicate(futures, Objects::nonNull);
    return future;
  }

  /**
   * Query the blaze targets that are direct dependencies for the given target.
   *
   * <p>Future returns null if this provider was unable to query the blaze targets.
   */
  Future<List<TargetInfo>> getDirectDependencyTargets(Project project, Label target);
}
