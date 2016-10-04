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
package com.google.idea.blaze.base.model.primitives;

import com.google.common.base.Preconditions;
import java.io.Serializable;

/**
 * An interface for objects that represent targets you could pass to Blaze on the command line. See
 * {@link com.google.idea.blaze.base.model.primitives.Label},
 */
public class TargetExpression implements Serializable, Comparable<TargetExpression> {
  public static final long serialVersionUID = 1L;

  private final String expression;

  /**
   * @return A Label instance if the expression is a valid label, or a TargetExpression instance if
   *     it is not.
   */
  public static TargetExpression fromString(String expression) {
    return Label.validate(expression) ? new Label(expression) : new TargetExpression(expression);
  }

  TargetExpression(String expression) {
    // TODO(joshgiles): Validation/canonicalization for target expressions.
    // For reference, handled in Blaze/Bazel in TargetPattern.java.
    Preconditions.checkArgument(!expression.isEmpty(), "Target should be non-empty.");
    this.expression = expression;
  }

  @Override
  public String toString() {
    return expression;
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof TargetExpression)) {
      return false;
    }
    TargetExpression that = (TargetExpression) o;
    return expression.equals(that.expression);
  }

  @Override
  public int hashCode() {
    return expression.hashCode();
  }

  /** All targets in all packages below the given path */
  public static TargetExpression allFromPackageRecursive(WorkspacePath localPackage) {
    if (localPackage.relativePath().isEmpty()) {
      // localPackage is the workspace root
      return new TargetExpression("//...:all");
    }
    return new TargetExpression("//" + localPackage.relativePath() + "/...:all");
  }

  public static TargetExpression allFromPackageNonRecursive(WorkspacePath localPackage) {
    return new TargetExpression("//" + localPackage.relativePath() + ":all");
  }

  @Override
  public int compareTo(TargetExpression o) {
    return expression.compareTo(o.expression);
  }
}
