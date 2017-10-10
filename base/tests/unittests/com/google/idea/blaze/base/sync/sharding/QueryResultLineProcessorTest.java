/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.base.sync.sharding;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.base.BlazeTestCase;
import com.google.idea.blaze.base.model.primitives.TargetExpression;
import com.google.idea.common.experiments.ExperimentService;
import com.google.idea.common.experiments.MockExperimentService;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link QueryResultLineProcessor}. */
@RunWith(JUnit4.class)
public class QueryResultLineProcessorTest extends BlazeTestCase {

  @Override
  protected void initTest(Container applicationServices, Container projectServices) {
    applicationServices.register(ExperimentService.class, new MockExperimentService());
  }

  @Test
  public void testRecognizesStandardResultLines() {
    ImmutableList.Builder<TargetExpression> output = ImmutableList.builder();
    QueryResultLineProcessor processor = new QueryResultLineProcessor(output, x -> true);

    processor.processLine("css_library rule //java/com/google/foo/styles:global");
    processor.processLine("java_library rule //java/com/google/bar/console:runtime_deps");

    ImmutableList<TargetExpression> parsedTargets = output.build();
    assertThat(parsedTargets)
        .containsExactly(
            TargetExpression.fromStringSafe("//java/com/google/foo/styles:global"),
            TargetExpression.fromStringSafe("//java/com/google/bar/console:runtime_deps"));
  }

  @Test
  public void testIgnoresNonRules() {
    ImmutableList.Builder<TargetExpression> output = ImmutableList.builder();
    QueryResultLineProcessor processor = new QueryResultLineProcessor(output, x -> true);

    processor.processLine("generated file //java/com/google/foo:libthrowable_utils.jar");
    processor.processLine("source file //java/com/google/foo:BUILD");
    processor.processLine("package group //java/com/google/foo:packages");

    assertThat(output.build()).isEmpty();
  }

  @Test
  public void testFilterRuleTypes() {
    ImmutableSet<String> acceptedRuleTypes =
        ImmutableSet.of("java_library", "custom_type", "sh_test");
    ImmutableList.Builder<TargetExpression> output = ImmutableList.builder();
    QueryResultLineProcessor processor =
        new QueryResultLineProcessor(output, t -> acceptedRuleTypes.contains(t.ruleType));

    processor.processLine("css_library rule //java/com/google/foo/styles:global");
    processor.processLine("java_library rule //java/com/google/bar/console:runtime_deps");
    processor.processLine("java_test rule //java/com/google/bar/console:test1");
    processor.processLine("test_suite rule //java/com/google/bar/console:all_tests");
    processor.processLine("custom_type rule //java/com/google/bar/console:custom");
    processor.processLine("sh_test rule //java/com/google/bar/console:sh_test");

    assertThat(output.build())
        .containsExactly(
            TargetExpression.fromStringSafe("//java/com/google/bar/console:runtime_deps"),
            TargetExpression.fromStringSafe("//java/com/google/bar/console:custom"),
            TargetExpression.fromStringSafe("//java/com/google/bar/console:sh_test"));
  }

  @Test
  public void testFilterRuleTypesRetainingExplicitlySpecifiedTargets() {
    ImmutableSet<String> acceptedRuleTypes =
        ImmutableSet.of("java_library", "custom_type", "sh_test");
    ImmutableSet<String> explicitTargets = ImmutableSet.of("//java/com/google/foo/styles:global");

    ImmutableList.Builder<TargetExpression> output = ImmutableList.builder();
    QueryResultLineProcessor processor =
        new QueryResultLineProcessor(
            output,
            t -> explicitTargets.contains(t.label) || acceptedRuleTypes.contains(t.ruleType));

    processor.processLine("css_library rule //java/com/google/foo/styles:global");
    processor.processLine("java_library rule //java/com/google/bar/console:runtime_deps");
    processor.processLine("java_test rule //java/com/google/bar/console:test1");
    processor.processLine("test_suite rule //java/com/google/bar/console:all_tests");
    processor.processLine("custom_type rule //java/com/google/bar/console:custom");
    processor.processLine("sh_test rule //java/com/google/bar/console:sh_test");

    assertThat(output.build())
        .containsExactly(
            TargetExpression.fromStringSafe("//java/com/google/foo/styles:global"),
            TargetExpression.fromStringSafe("//java/com/google/bar/console:runtime_deps"),
            TargetExpression.fromStringSafe("//java/com/google/bar/console:custom"),
            TargetExpression.fromStringSafe("//java/com/google/bar/console:sh_test"));
  }
}
