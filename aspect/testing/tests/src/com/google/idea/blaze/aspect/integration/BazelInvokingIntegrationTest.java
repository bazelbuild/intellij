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
package com.google.idea.blaze.aspect.integration;

import static com.google.idea.blaze.base.sync.aspects.strategy.AspectStrategy.getOutputGroups;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import build.bazel.tests.integration.BazelCommand;
import build.bazel.tests.integration.WorkspaceDriver;
import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.sync.aspects.strategy.AspectStrategy.OutputGroup;
import com.google.idea.blaze.base.sync.aspects.strategy.AspectStrategyBazel;
import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * A Bazel-invoking integration test for the bundled IntelliJ aspect.
 *
 * <p>These tests assert the end-to-end behavior of the plugin's aspect during a sync, and ensure
 * that it generates the correct IDE info files.
 */
public class BazelInvokingIntegrationTest {

  // Flags for wiring up the plugin aspect from the external @intellij_aspect repository.
  public static final ImmutableList<String> ASPECT_FLAGS =
      ImmutableList.of(
          AspectStrategyBazel.ASPECT_FLAG,
          String.format(
              "%s=%s/%s/aspect",
              AspectStrategyBazel.OVERRIDE_REPOSITORY_FLAG,
              System.getenv("TEST_SRCDIR"),
              System.getenv("TEST_WORKSPACE")));
  private WorkspaceDriver driver = new WorkspaceDriver();

  @BeforeClass
  public static void setUpClass() throws IOException {
    WorkspaceDriver.setUpClass();
  }

  @Before
  public void setUp() throws Exception {
    driver.setUp();
  }

  @Test
  public void aspect_genericOutputGroup_generatesInfoTxt() throws Exception {
    driver.scratchFile("foo/BUILD", "sh_test(name = \"bar\",\n" + "srcs = [\"bar.sh\"])");
    driver.scratchExecutableFile("foo/bar.sh", "echo \"bar\"", "exit 0");

    ImmutableList.Builder<String> args = ImmutableList.builder();
    args.add("build");
    args.add("//foo:bar");
    args.add("--define=ij_product=intellij-latest");
    args.addAll(ASPECT_FLAGS);
    args.add(
        getOutputGroupsFlag(
            ImmutableList.of(OutputGroup.INFO), ImmutableList.of(LanguageClass.GENERIC)));

    BazelCommand cmd = driver.bazel(args.build()).runVerbose();

    assertEquals("return code is 0", 0, cmd.exitCode());

    // Bazel's output goes into stderr by default, even on success.
    assertTrue(
        "stderr contains up-to-date message",
        cmd.errorLines().stream().anyMatch(line -> line.endsWith("//foo:bar up-to-date:")));
    assertTrue(
        "stderr contains intellij-info.txt filename",
        cmd.errorLines().stream()
            .anyMatch(line -> line.endsWith(getIntelliJInfoTxtFilename("bar"))));
  }

  private String getIntelliJInfoTxtFilename(String targetName) {
    // e.g. bar-97299.intellij-info.txt
    // The hashCode() call here is the implementation function underlying the Starlark hash()
    // function in used in intellij-info-impl.bzl.
    return String.format("%s-%s.intellij-info.txt", targetName, targetName.hashCode());
  }

  private String getOutputGroupsFlag(
      Collection<OutputGroup> outputGroups, Collection<LanguageClass> languageClassList) {
    // e.g. --output_groups=intellij-info-generic,intellij-resolve-java,intellij-compile-java
    Set<LanguageClass> languageClasses = new HashSet<>(languageClassList);
    String outputGroupNames =
        outputGroups.stream()
            .flatMap(g -> getOutputGroups(g, languageClasses, /* directDepsOnly= */ false).stream())
            .collect(Collectors.joining(","));
    return "--output_groups=" + outputGroupNames;
  }
}
