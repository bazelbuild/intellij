/*
 * Copyright 2018 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.scala;

import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.model.primitives.RuleType;
import java.util.Arrays;

/** Contributes scala rules to {@link Kind}. */
public final class ScalaBlazeRules implements Kind.Provider {

  /** Scala-specific blaze rule types. */
  public enum RuleTypes {
    SCALA_BINARY("scala_binary", LanguageClass.SCALA, RuleType.BINARY),
    SCALA_IMPORT("scala_import", LanguageClass.SCALA, RuleType.UNKNOWN),
    SCALA_LIBRARY("scala_library", LanguageClass.SCALA, RuleType.LIBRARY),
    SCALA_MACRO_LIBRARY("scala_macro_library", LanguageClass.SCALA, RuleType.LIBRARY),
    SCALA_TEST("scala_test", LanguageClass.SCALA, RuleType.TEST),
    SCALA_JUNIT_TEST("scala_junit_test", LanguageClass.SCALA, RuleType.TEST);

    private final String name;
    private final LanguageClass languageClass;
    private final RuleType ruleType;

    RuleTypes(String name, LanguageClass languageClass, RuleType ruleType) {
      this.name = name;
      this.languageClass = languageClass;
      this.ruleType = ruleType;
    }

    public Kind getKind() {
      return Preconditions.checkNotNull(Kind.fromRuleName(name));
    }
  }

  @Override
  public ImmutableSet<Kind> getTargetKinds() {
    return Arrays.stream(RuleTypes.values())
        .map(e -> Kind.Provider.create(e.name, e.languageClass, e.ruleType))
        .collect(toImmutableSet());
  }
}
