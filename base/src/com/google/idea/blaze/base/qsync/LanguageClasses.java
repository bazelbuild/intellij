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

import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.qsync.project.LanguageClassProto.LanguageClass;
import java.util.Collection;
import java.util.Optional;

/** A utility class to translate variants of {@link LanguageClass} enumerations. */
class LanguageClasses {

  private LanguageClasses() {}

  /**
   * Translates a set of {@link com.google.idea.blaze.base.model.primitives.LanguageClass} values to
   * a set of {@link LanguageClass} values retaining only values meaningful in the query sync
   * context.
   */
  static ImmutableSet<LanguageClass> translateFrom(
      Collection<com.google.idea.blaze.base.model.primitives.LanguageClass> from) {
    return from.stream().flatMap(it -> translateFrom(it).stream()).collect(toImmutableSet());
  }

  /**
   * Translates a value of {@link com.google.idea.blaze.base.model.primitives.LanguageClass} to a
   * value of {@link LanguageClass}, if it has a meaning in the query sync context.
   */
  static Optional<LanguageClass> translateFrom(
      com.google.idea.blaze.base.model.primitives.LanguageClass from) {
    switch (from) {
      case JAVA:
        return Optional.of(LanguageClass.JAVA);
      case KOTLIN:
        return Optional.of(LanguageClass.KOTLIN);
      case C:
        return Optional.of(LanguageClass.CC);
      default:
        return Optional.<LanguageClass>empty();
    }
  }
}
