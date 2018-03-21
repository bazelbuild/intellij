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
package com.google.idea.blaze.base.run;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.JdkFutureAdapters;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.idea.blaze.base.dependencies.TargetInfo;
import com.google.idea.blaze.base.model.primitives.RuleType;
import com.google.idea.blaze.base.run.targetfinder.FuturesUtil;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Future;

/** Locates blaze rules which build a given source file. */
public interface SourceToTargetFinder {

  ExtensionPointName<SourceToTargetFinder> EP_NAME =
      ExtensionPointName.create("com.google.idea.blaze.SourceToTargetFinder");

  /**
   * Finds all rules of the given type 'reachable' from source file (i.e. with source included in
   * srcs, deps or runtime_deps).
   */
  Future<? extends Collection<TargetInfo>> targetsForSourceFile(
      Project project, File sourceFile, Optional<RuleType> ruleType);

  /**
   * Iterates through the all {@link SourceToTargetFinder}'s, returning a {@link Future}
   * representing the first non-empty result.
   */
  static ListenableFuture<Collection<TargetInfo>> findTargetInfoFuture(
      Project project, File sourceFile, Optional<RuleType> ruleType) {
    List<ListenableFuture<? extends Collection<TargetInfo>>> futures = new ArrayList<>();
    for (SourceToTargetFinder finder : EP_NAME.getExtensions()) {
      Future<? extends Collection<TargetInfo>> future =
          finder.targetsForSourceFile(project, sourceFile, ruleType);
      if (future.isDone() && futures.isEmpty()) {
        Collection<TargetInfo> targets = FuturesUtil.getIgnoringErrors(future);
        if (targets != null && !targets.isEmpty()) {
          return Futures.immediateFuture(targets);
        }
      } else {
        // we can't return ListenableFuture directly, because implementations are using different
        // versions of that class...
        futures.add(JdkFutureAdapters.listenInPoolThread(future));
      }
    }
    if (futures.isEmpty()) {
      return Futures.immediateFuture(ImmutableList.of());
    }
    return Futures.transform(
        Futures.allAsList(futures),
        (Function<List<Collection<TargetInfo>>, Collection<TargetInfo>>)
            list ->
                list == null
                    ? null
                    : list.stream()
                        .filter(t -> t != null && !t.isEmpty())
                        .findFirst()
                        .orElse(ImmutableList.of()));
  }

  /**
   * Iterates through all {@link SourceToTargetFinder}s, returning the first immediately available,
   * non-empty result.
   */
  static Collection<TargetInfo> findTargetsForSourceFile(
      Project project, File sourceFile, Optional<RuleType> ruleType) {
    for (SourceToTargetFinder finder : EP_NAME.getExtensions()) {
      Future<? extends Collection<TargetInfo>> future =
          finder.targetsForSourceFile(project, sourceFile, ruleType);
      if (!future.isDone()) {
        continue;
      }
      Collection<TargetInfo> targets = FuturesUtil.getIgnoringErrors(future);
      if (targets != null && !targets.isEmpty()) {
        return targets;
      }
    }
    return ImmutableList.of();
  }
}
