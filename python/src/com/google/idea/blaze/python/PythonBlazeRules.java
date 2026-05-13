/*
 * Copyright 2024 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.python;

import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.devtools.intellij.ideinfo.IntellijIdeInfo.TargetIdeInfo;
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.model.primitives.RuleType;
import java.util.Arrays;
import java.util.function.Function;

/** Contributes python rules to {@link Kind}. */
public final class PythonBlazeRules implements Kind.Provider {

  public enum RuleTypes {
    PY_LIBRARY("py_library", RuleType.LIBRARY),
    PY_BINARY("py_binary", RuleType.BINARY),
    PY_TEST("py_test", RuleType.TEST);

    private final String name;
    private final RuleType ruleType;

    RuleTypes(String name, RuleType ruleType) {
      this.name = name;
      this.ruleType = ruleType;
    }

    public Kind getKind() {
      return Preconditions.checkNotNull(Kind.fromRuleName(name));
    }
  }

  @Override
  public ImmutableSet<Kind> getTargetKinds() {
    return Arrays.stream(RuleTypes.values())
        .map(e -> Kind.Provider.create(e.name, LanguageClass.PYTHON, e.ruleType))
        .collect(toImmutableSet());
  }

  @Override
  public Function<TargetIdeInfo, Kind> getTargetKindHeuristics() {
    return (info) -> {
      if (info.hasPyIdeInfo() && info.getPyIdeInfo().getIsCodeGenerator()) {
        return RuleTypes.PY_LIBRARY.getKind();
      }

      return null;
    };
  }

}
