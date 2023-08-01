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
package com.google.idea.blaze.base.qsync;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.common.Label;
import java.util.Collection;
import java.util.Optional;

/** The result of resolving a source file or directory to build targets. */
@AutoValue
public abstract class TargetsToBuild {

  /** The type of this target set, determines the semantics of how it should be used. */
  public enum Type {
    /** All targets returned should be built. This is used for directories and build files. */
    BUILD_ALL,
    /**
     * Just one of the returned targets needs to be built. This is used for regular source files,
     * and the associated {@link #targets()} represent all build rules that use that file as source.
     */
    CHOOSE_ONE
  }

  public static final TargetsToBuild NONE =
      new AutoValue_TargetsToBuild(Type.BUILD_ALL, ImmutableSet.of());

  public abstract Type type();

  public abstract ImmutableSet<Label> targets();

  public boolean isEmpty() {
    return targets().isEmpty();
  }

  /**
   * Indicates if this set of targets to build is ambiguous.
   *
   * @return {code true} if the targets derive from a source file, and that source file is built by
   *     more than one target.
   */
  public boolean isAmbiguous() {
    return type() == Type.CHOOSE_ONE && targets().size() > 1;
  }

  public Optional<ImmutableSet<Label>> getUnambiguousTargets() {
    return isAmbiguous() ? Optional.empty() : Optional.of(targets());
  }

  static TargetsToBuild buildAll(Collection<Label> targets) {
    return new AutoValue_TargetsToBuild(Type.BUILD_ALL, ImmutableSet.copyOf(targets));
  }

  static TargetsToBuild chooseOne(Collection<Label> targets) {
    return new AutoValue_TargetsToBuild(Type.CHOOSE_ONE, ImmutableSet.copyOf(targets));
  }
}
