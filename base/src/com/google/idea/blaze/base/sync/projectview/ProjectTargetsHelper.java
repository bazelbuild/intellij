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
import java.util.List;
import javax.annotation.Nullable;

/** Identifies targets covered by the .blazeproject 'targets' section, as it's seen by blaze. */
final class ProjectTargetsHelper {

  static ProjectTargetsHelper create(List<TargetExpression> projectTargets) {
    return new ProjectTargetsHelper(
        projectTargets.stream().map(ProjectTarget::new).collect(toImmutableList()));
  }

  /**
   * The list of the project targets, in the reverse order to which they're passed to blaze, since
   * later target expressions override earlier ones.
   */
  private final ImmutableList<ProjectTarget> reversedTargets;

  private ProjectTargetsHelper(List<ProjectTarget> projectTargets) {
    this.reversedTargets = ImmutableList.copyOf(projectTargets).reverse();
  }

  boolean isInProject(Label label) {
    // the last target expression to cover this label overrides all previous expressions
    for (ProjectTarget target : reversedTargets) {
      if (target.coversTarget(label)) {
        return !target.isExcluded();
      }
    }
    return false;
  }

  private static class ProjectTarget {
    private final TargetExpression originalExpression;
    private final TargetExpression unexcludedExpression;
    @Nullable private final WildcardTargetPattern wildcardPattern;

    ProjectTarget(TargetExpression expression) {
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
      return label.equals(unexcludedExpression)
          || (wildcardPattern != null && wildcardPattern.coversPackage(label.blazePackage()));
    }
  }
}
