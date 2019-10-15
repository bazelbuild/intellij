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
package com.google.idea.blaze.base.sync.projectview;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.TargetExpression;
import com.google.idea.blaze.base.model.primitives.WildcardTargetPattern;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.sync.projectview.ImportRoots.ProjectDirectoriesHelper;
import java.util.List;
import javax.annotation.Nullable;

/**
 * Identifies targets/sources covered by an ordered list of {@link TargetExpression}.
 *
 * <p>Handles a mixture of included and excluded target expressions.
 */
public final class TargetExpressionList {

  public static TargetExpressionList create(List<TargetExpression> targets) {
    return new TargetExpressionList(
        targets.stream().map(TargetData::new).collect(toImmutableList()), null);
  }

  static TargetExpressionList createWithTargetsDerivedFromDirectories(
      List<TargetExpression> targets, ProjectDirectoriesHelper directories) {
    return new TargetExpressionList(
        targets.stream().map(TargetData::new).collect(toImmutableList()), directories);
  }

  /**
   * The list of the project targets, in the reverse order to which they're passed to blaze, since
   * later target expressions override earlier ones.
   */
  private final ImmutableList<TargetData> reversedTargets;

  /** Non-null if we're auto-including targets derived from the project directories. */
  @Nullable private final ProjectDirectoriesHelper directories;

  private TargetExpressionList(
      List<TargetData> projectTargets, @Nullable ProjectDirectoriesHelper directories) {
    this.reversedTargets = ImmutableList.copyOf(projectTargets).reverse();
    this.directories = directories;
  }

  /** Returns true if the entire package is covered by the target expressions. */
  boolean includesPackage(WorkspacePath packagePath) {
    // the last target expression to cover this label overrides all previous expressions
    for (TargetData target : reversedTargets) {
      if (target.coversPackage(packagePath)) {
        return !target.isExcluded();
      }
    }
    return directories != null && directories.containsWorkspacePath(packagePath);
  }

  /** Returns true if the individual target is covered by this list. */
  public boolean includesTarget(Label label) {
    // the last target expression to cover this label overrides all previous expressions
    for (TargetData target : reversedTargets) {
      if (target.coversTarget(label)) {
        return !target.isExcluded();
      }
    }
    return directories != null && directories.containsWorkspacePath(label.blazePackage());
  }

  /** A single {@link TargetExpression} and associated information. */
  private static class TargetData {
    private final TargetExpression originalExpression;
    private final TargetExpression unexcludedExpression;
    @Nullable private final WildcardTargetPattern wildcardPattern;

    TargetData(TargetExpression expression) {
      this.originalExpression = expression;
      this.unexcludedExpression =
          expression.isExcluded()
              ? TargetExpression.fromStringSafe(expression.toString().substring(1))
              : expression;
      this.wildcardPattern = WildcardTargetPattern.fromExpression(expression);
    }

    boolean isExcluded() {
      return originalExpression.isExcluded();
    }

    boolean coversTarget(Label label) {
      return label.equals(unexcludedExpression) || coversPackage(label.blazePackage());
    }

    boolean coversPackage(WorkspacePath path) {
      return wildcardPattern != null && wildcardPattern.coversPackage(path);
    }
  }
}
