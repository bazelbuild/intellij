/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.sync.aspects.strategy;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.base.BlazeTestCase;
import com.google.idea.blaze.base.command.BlazeCommand;
import com.google.idea.blaze.base.command.BlazeCommandName;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.common.experiments.ExperimentService;
import com.google.idea.common.experiments.MockExperimentService;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link AspectStrategy}. */
@RunWith(JUnit4.class)
public class AspectStrategyTest extends BlazeTestCase {

  @Override
  protected void initTest(Container applicationServices, Container projectServices) {
    applicationServices.register(ExperimentService.class, new MockExperimentService());
  }

  @Test
  public void testLegacyOutputGroupsUnchanged() {
    AspectStrategy strategy = MockAspectStrategy.noPerLanguageOutputGroups();
    Set<LanguageClass> activeLanguages = ImmutableSet.of(LanguageClass.JAVA, LanguageClass.ANDROID);

    BlazeCommand.Builder builder = emptyBuilder();
    strategy.modifyIdeInfoCommand(builder, activeLanguages);
    assertThat(getBlazeFlags(builder)).containsExactly("--output_groups=intellij-info-text");

    builder = emptyBuilder();
    strategy.modifyIdeResolveCommand(builder, activeLanguages);
    assertThat(getBlazeFlags(builder)).containsExactly("--output_groups=intellij-resolve");

    builder = emptyBuilder();
    strategy.modifyIdeCompileCommand(builder, activeLanguages);
    assertThat(getBlazeFlags(builder)).containsExactly("--output_groups=intellij-compile");
  }

  @Test
  public void testGenericOutputGroupAlwaysPresent() {
    AspectStrategy strategy = MockAspectStrategy.withPerLanguageOutputGroups();
    Set<LanguageClass> activeLanguages = ImmutableSet.of();

    BlazeCommand.Builder builder = emptyBuilder();
    strategy.modifyIdeInfoCommand(builder, activeLanguages);
    assertThat(getOutputGroups(builder)).containsExactly("intellij-info-generic");
  }

  @Test
  public void testNoGenericOutputGroupInResolveOrCompile() {
    AspectStrategy strategy = MockAspectStrategy.withPerLanguageOutputGroups();
    Set<LanguageClass> activeLanguages = ImmutableSet.of(LanguageClass.JAVA);

    BlazeCommand.Builder builder = emptyBuilder();
    strategy.modifyIdeResolveCommand(builder, activeLanguages);
    assertThat(getOutputGroups(builder)).containsExactly("intellij-resolve-java");

    builder = emptyBuilder();
    strategy.modifyIdeCompileCommand(builder, activeLanguages);
    assertThat(getOutputGroups(builder)).containsExactly("intellij-compile-java");
  }

  @Test
  public void testAllPerLanguageOutputGroupsRecognized() {
    AspectStrategy strategy = MockAspectStrategy.withPerLanguageOutputGroups();
    Set<LanguageClass> activeLanguages =
        Arrays.stream(LanguageOutputGroup.values())
            .map(lang -> lang.languageClass)
            .collect(Collectors.toSet());

    BlazeCommand.Builder builder = emptyBuilder();
    strategy.modifyIdeInfoCommand(builder, activeLanguages);
    assertThat(getOutputGroups(builder))
        .containsExactly(
            "intellij-info-generic",
            "intellij-info-java",
            "intellij-info-cpp",
            "intellij-info-android",
            "intellij-info-py",
            "intellij-info-go",
            "intellij-info-js",
            "intellij-info-ts",
            "intellij-info-dart");

    builder = emptyBuilder();
    strategy.modifyIdeResolveCommand(builder, activeLanguages);
    assertThat(getOutputGroups(builder))
        .containsExactly(
            "intellij-resolve-java",
            "intellij-resolve-cpp",
            "intellij-resolve-android",
            "intellij-resolve-py",
            "intellij-resolve-go",
            "intellij-resolve-js",
            "intellij-resolve-ts",
            "intellij-resolve-dart");

    builder = emptyBuilder();
    strategy.modifyIdeCompileCommand(builder, activeLanguages);
    assertThat(getOutputGroups(builder))
        .containsExactly(
            "intellij-compile-java",
            "intellij-compile-cpp",
            "intellij-compile-android",
            "intellij-compile-py",
            "intellij-compile-go",
            "intellij-compile-js",
            "intellij-compile-ts",
            "intellij-compile-dart");
  }

  private static BlazeCommand.Builder emptyBuilder() {
    return BlazeCommand.builder("/usr/bin/blaze", BlazeCommandName.BUILD);
  }

  private static ImmutableList<String> getBlazeFlags(BlazeCommand.Builder builder) {
    ImmutableList<String> args = builder.build().toList();
    return args.subList(3, args.indexOf("--"));
  }

  private static ImmutableList<String> getOutputGroups(BlazeCommand.Builder builder) {
    List<String> blazeFlags = getBlazeFlags(builder);
    assertThat(blazeFlags).hasSize(1);
    String groups = blazeFlags.get(0).substring("--output_groups=".length());
    return ImmutableList.copyOf(groups.split(","));
  }

  private static class MockAspectStrategy extends AspectStrategy {

    static MockAspectStrategy withPerLanguageOutputGroups() {
      return new MockAspectStrategy(true);
    }

    static MockAspectStrategy noPerLanguageOutputGroups() {
      return new MockAspectStrategy(false);
    }

    final boolean hasPerLanguageOutputGroups;

    private MockAspectStrategy(boolean hasPerLanguageOutputGroups) {
      this.hasPerLanguageOutputGroups = hasPerLanguageOutputGroups;
    }

    @Override
    public String getName() {
      return "MockAspectStrategy";
    }

    @Override
    protected List<String> getAspectFlags() {
      return ImmutableList.of();
    }

    @Override
    protected boolean hasPerLanguageOutputGroups() {
      return hasPerLanguageOutputGroups;
    }
  }
}
