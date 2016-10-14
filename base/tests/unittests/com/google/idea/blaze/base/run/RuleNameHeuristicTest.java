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

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.BlazeTestCase;
import com.google.idea.blaze.base.ideinfo.RuleIdeInfo;
import com.google.idea.blaze.base.model.primitives.Label;
import com.intellij.openapi.extensions.impl.ExtensionPointImpl;
import java.io.File;
import java.util.Collection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link RuleNameHeuristic}. */
@RunWith(JUnit4.class)
public class RuleNameHeuristicTest extends BlazeTestCase {

  @Override
  protected void initTest(Container applicationServices, Container projectServices) {
    super.initTest(applicationServices, projectServices);

    ExtensionPointImpl<TestRuleHeuristic> ep =
        registerExtensionPoint(TestRuleHeuristic.EP_NAME, TestRuleHeuristic.class);
    ep.registerExtension(new RuleNameHeuristic());
  }

  @Test
  public void testPredicateMatchingName() throws Exception {
    File source = new File("java/com/foo/FooTest.java");
    RuleIdeInfo rule = RuleIdeInfo.builder().setLabel("//foo:FooTest").setKind("java_test").build();
    assertThat(new RuleNameHeuristic().matchesSource(rule, source, null)).isTrue();
  }

  @Test
  public void testPredicateMatchingNameAndPath() throws Exception {
    File source = new File("java/com/foo/FooTest.java");
    RuleIdeInfo rule =
        RuleIdeInfo.builder().setLabel("//foo:foo/FooTest").setKind("java_test").build();
    assertThat(new RuleNameHeuristic().matchesSource(rule, source, null)).isTrue();
  }

  @Test
  public void testPredicateNotMatchingForPartialOverlap() throws Exception {
    File source = new File("java/com/foo/BarFooTest.java");
    RuleIdeInfo rule = RuleIdeInfo.builder().setLabel("//foo:FooTest").setKind("java_test").build();
    assertThat(new RuleNameHeuristic().matchesSource(rule, source, null)).isFalse();
  }

  @Test
  public void testPredicateNotMatchingIncorrectPath() throws Exception {
    File source = new File("java/com/foo/FooTest.java");
    RuleIdeInfo rule =
        RuleIdeInfo.builder().setLabel("//foo:bar/FooTest").setKind("java_test").build();
    assertThat(new RuleNameHeuristic().matchesSource(rule, source, null)).isFalse();
  }

  @Test
  public void testPredicateDifferentName() throws Exception {
    File source = new File("java/com/foo/FooTest.java");
    RuleIdeInfo rule = RuleIdeInfo.builder().setLabel("//foo:ForTest").setKind("java_test").build();
    assertThat(new RuleNameHeuristic().matchesSource(rule, source, null)).isFalse();
  }

  @Test
  public void testFilterNoMatchesFallBackToFirstRule() throws Exception {
    File source = new File("java/com/foo/FooTest.java");
    Collection<RuleIdeInfo> rules =
        ImmutableList.of(
            RuleIdeInfo.builder().setLabel("//foo:FirstTest").setKind("java_test").build(),
            RuleIdeInfo.builder().setLabel("//bar:OtherTest").setKind("java_test").build());
    Label match = TestRuleHeuristic.chooseTestTargetForSourceFile(source, rules, null);
    assertThat(match).isEqualTo(new Label("//foo:FirstTest"));
  }

  @Test
  public void testFilterOneMatch() throws Exception {
    File source = new File("java/com/foo/FooTest.java");
    Collection<RuleIdeInfo> rules =
        ImmutableList.of(
            RuleIdeInfo.builder().setLabel("//bar:FirstTest").setKind("java_test").build(),
            RuleIdeInfo.builder().setLabel("//foo:FooTest").setKind("java_test").build());
    Label match = TestRuleHeuristic.chooseTestTargetForSourceFile(source, rules, null);
    assertThat(match).isEqualTo(new Label("//foo:FooTest"));
  }

  @Test
  public void testFilterChoosesFirstMatch() throws Exception {
    File source = new File("java/com/foo/FooTest.java");
    Collection<RuleIdeInfo> rules =
        ImmutableList.of(
            RuleIdeInfo.builder().setLabel("//bar:OtherTest").setKind("java_test").build(),
            RuleIdeInfo.builder().setLabel("//foo:FooTest").setKind("java_test").build(),
            RuleIdeInfo.builder().setLabel("//bar/foo:FooTest").setKind("java_test").build());
    Label match = TestRuleHeuristic.chooseTestTargetForSourceFile(source, rules, null);
    assertThat(match).isEqualTo(new Label("//foo:FooTest"));
  }
}
