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

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableListMultimap;
import com.google.idea.blaze.base.model.primitives.Label;
import com.intellij.psi.PsiReference;

/**
 * A mapping between a PSI reference in a specific source file and the rules building the source
 * file that are missing dependencies for the PSI reference. A list of eligible Blaze target rules
 * to fix the missing dependency is provided for each source file rule.
 */
@AutoValue
public abstract class MissingDependencyData {
  /** Returns the PSI reference in the source file. */
  public abstract PsiReference reference();

  /**
   * Returns the Blaze targets that can be added to each source file rule to fix the dependency.
   *
   * <p>The key of the multimap is the label of the source file rule and the value is the Blaze
   * target to fix the dependency.
   */
  public abstract ImmutableListMultimap<Label, Label> dependencyTargets();

  public static Builder builder() {
    return new AutoValue_MissingDependencyData.Builder();
  }

  /** Builder for {@link MissingDependencyData}. */
  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder setReference(PsiReference value);

    public abstract Builder setDependencyTargets(
        ImmutableListMultimap<Label, Label> dependencyTargets);

    public abstract MissingDependencyData build();
  }
}
