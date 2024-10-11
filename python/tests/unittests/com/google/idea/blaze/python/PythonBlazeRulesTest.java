/*
 * Copyright 2024 The Bazel Authors. All rights reserved.
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

package com.google.idea.blaze.python;

import com.google.common.collect.ImmutableSet;
import com.google.devtools.intellij.ideinfo.IntellijIdeInfo;
import com.google.devtools.intellij.ideinfo.IntellijIdeInfo.PyIdeInfo;
import com.google.devtools.intellij.ideinfo.IntellijIdeInfo.TargetIdeInfo;
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.model.primitives.RuleType;
import java.util.function.Function;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import static com.google.common.truth.Truth.assertThat;

@RunWith(JUnit4.class)
public class PythonBlazeRulesTest {

  private final static Kind.Provider rules = new PythonBlazeRules();

  @Test
  public void testTargetKindHeuristicsForCodeGenerator() {
    Function<TargetIdeInfo, Kind> heuristics = rules.getTargetKindHeuristics();
    TargetIdeInfo targetInfo = TargetIdeInfo.newBuilder()
        .setPyIdeInfo(PyIdeInfo.newBuilder().setIsCodeGenerator(true).build())
        .build();

    // code under test
    Kind kind = heuristics.apply(targetInfo);

    assertThat(kind).isNotNull();
    assertThat(kind.getRuleType()).isEqualTo(RuleType.LIBRARY);
    assertThat(kind.getLanguageClasses()).containsExactly(LanguageClass.PYTHON);
  }

  @Test
  public void testTargetKindHeuristicsForNonCodeGenerator() {
    Function<TargetIdeInfo, Kind> heuristics = rules.getTargetKindHeuristics();
    TargetIdeInfo targetInfo = TargetIdeInfo.newBuilder()
        .addAllTags(ImmutableSet.of("manual"))
        .build();

    // code under test
    Kind kind = heuristics.apply(targetInfo);

    assertThat(kind).isNull();
  }

}
