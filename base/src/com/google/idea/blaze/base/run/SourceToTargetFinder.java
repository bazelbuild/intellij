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

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.dependencies.TargetInfo;
import com.google.idea.blaze.base.model.primitives.RuleType;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import java.io.File;
import java.util.Collection;
import java.util.Optional;

/**
 * Locates blaze rules which build a given source file.
 *
 * <p>TODO(brendandouglas): Combine with SourceToTargetProvider.
 */
public interface SourceToTargetFinder {

  ExtensionPointName<SourceToTargetFinder> EP_NAME =
      ExtensionPointName.create("com.google.idea.blaze.SourceToTargetFinder");

  /**
   * Iterates through the available {@link SourceToTargetFinder}'s, returning results from the first
   * one providing targets for this source file.
   */
  static Collection<TargetInfo> findTargetsForSourceFile(
      Project project, File sourceFile, Optional<RuleType> ruleType) {
    for (SourceToTargetFinder finder : EP_NAME.getExtensions()) {
      Collection<TargetInfo> targets = finder.targetsForSourceFile(project, sourceFile, ruleType);
      if (!targets.isEmpty()) {
        return targets;
      }
    }
    return ImmutableList.of();
  }

  /**
   * Finds all rules of the given type 'reachable' from source file (i.e. with source included in
   * srcs, deps or runtime_deps).
   */
  Collection<TargetInfo> targetsForSourceFile(
      Project project, File sourceFile, Optional<RuleType> ruleType);
}
