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
import com.google.idea.blaze.base.BlazeIntegrationTestCase;
import com.google.idea.blaze.base.ideinfo.RuleIdeInfo;
import com.google.idea.blaze.base.ideinfo.TestIdeInfo;
import com.google.idea.blaze.base.ideinfo.TestIdeInfo.TestSize;
import com.google.idea.blaze.base.model.primitives.Label;
import java.io.File;
import java.util.Collection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Integration tests for {@link TestRuleHeuristic}. */
@RunWith(JUnit4.class)
public class TestRuleHeuristicTest extends BlazeIntegrationTestCase {

  @Test
  public void testTestSizeMatched() throws Exception {
    File source = new File("java/com/foo/FooTest.java");
    Collection<RuleIdeInfo> rules =
        ImmutableList.of(
            RuleIdeInfo.builder()
                .setLabel("//foo:test1")
                .setKind("java_test")
                .setTestInfo(TestIdeInfo.builder().setTestSize(TestSize.MEDIUM))
                .build(),
            RuleIdeInfo.builder()
                .setLabel("//foo:test2")
                .setKind("java_test")
                .setTestInfo(TestIdeInfo.builder().setTestSize(TestSize.SMALL))
                .build());
    Label match = TestRuleHeuristic.chooseTestTargetForSourceFile(source, rules, TestSize.SMALL);
    assertThat(match).isEqualTo(new Label("//foo:test2"));
  }

  @Test
  public void testRuleNameMatched() throws Exception {
    File source = new File("java/com/foo/FooTest.java");
    Collection<RuleIdeInfo> rules =
        ImmutableList.of(
            RuleIdeInfo.builder().setLabel("//foo:FirstTest").setKind("java_test").build(),
            RuleIdeInfo.builder().setLabel("//foo:FooTest").setKind("java_test").build());
    Label match = TestRuleHeuristic.chooseTestTargetForSourceFile(source, rules, null);
    assertThat(match).isEqualTo(new Label("//foo:FooTest"));
  }

  @Test
  public void testNoMatchFallBackToFirstRule() throws Exception {
    File source = new File("java/com/foo/FooTest.java");
    ImmutableList<RuleIdeInfo> rules =
        ImmutableList.of(
            RuleIdeInfo.builder()
                .setLabel("//bar:BarTest")
                .setKind("java_test")
                .setTestInfo(TestIdeInfo.builder().setTestSize(TestSize.MEDIUM))
                .build(),
            RuleIdeInfo.builder()
                .setLabel("//foo:OtherTest")
                .setKind("java_test")
                .setTestInfo(TestIdeInfo.builder().setTestSize(TestSize.SMALL))
                .build());
    Label match = TestRuleHeuristic.chooseTestTargetForSourceFile(source, rules, TestSize.LARGE);
    assertThat(match).isEqualTo(new Label("//bar:BarTest"));
  }

  @Test
  public void testRuleNameCheckedBeforeTestSize() throws Exception {
    File source = new File("java/com/foo/FooTest.java");
    ImmutableList<RuleIdeInfo> rules =
        ImmutableList.of(
            RuleIdeInfo.builder()
                .setLabel("//bar:BarTest")
                .setKind("java_test")
                .setTestInfo(TestIdeInfo.builder().setTestSize(TestSize.SMALL))
                .build(),
            RuleIdeInfo.builder()
                .setLabel("//foo:FooTest")
                .setKind("java_test")
                .setTestInfo(TestIdeInfo.builder().setTestSize(TestSize.MEDIUM))
                .build());
    Label match = TestRuleHeuristic.chooseTestTargetForSourceFile(source, rules, TestSize.SMALL);
    assertThat(match).isEqualTo(new Label("//foo:FooTest"));
  }
}
