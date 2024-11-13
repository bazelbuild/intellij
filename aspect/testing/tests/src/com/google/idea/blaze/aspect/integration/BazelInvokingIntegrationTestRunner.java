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

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.bazel.BazelVersion;
import com.google.idea.blaze.base.model.BlazeVersionData;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.sync.aspects.strategy.AspectStrategy;
import com.google.idea.blaze.base.sync.aspects.strategy.AspectStrategy.OutputGroup;
import com.google.idea.blaze.base.sync.aspects.strategy.AspectStrategyBazel;
import com.google.idea.blaze.base.sync.aspects.strategy.AspectRepositoryProvider;
import java.io.File;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A Bazel-invoking integration test for the bundled IntelliJ aspect.
 *
 * <p>These tests assert the end-to-end behavior of the plugin's aspect during a sync, and ensure
 * that it generates the correct IDE info files.
 */
public class BazelInvokingIntegrationTestRunner {

  public static void main(String[] a) throws Exception {
    BazelVersion bazelVersion = getBazelVersion();
    if (bazelVersion == null) {
      exitWithError(
          String.format(
              "Failed to get Bazel version from Bazel path (%s)",
              System.getenv("BIT_BAZEL_BINARY")));
    }
    AspectStrategyBazel aspectStrategyBazel =
        new AspectStrategyBazel(BlazeVersionData.builder().setBazelVersion(bazelVersion).build());

    // Flags for wiring up the plugin aspect from the external @intellij_aspect repository.
    ImmutableList<String> aspectFlags =
        ImmutableList.of(
            aspectStrategyBazel.getAspectFlag().get(),
            String.format(
                "%s=%s/%s/aspect",
                AspectRepositoryProvider.overrideRepositoryFlag(false),
                System.getenv("TEST_SRCDIR"),
                System.getenv("TEST_WORKSPACE")),
            String.format(
              "%s=%s/%s/aspect_template",
              AspectRepositoryProvider.overrideRepositoryTemplateFlag(false),
              System.getenv("TEST_SRCDIR"),
              System.getenv("TEST_WORKSPACE"))
        );

    if (bazelVersion.isAtLeast(6, 0, 0)
        && !aspectFlags.contains(
            "--aspects=@@intellij_aspect//:intellij_info_bundled.bzl%intellij_info_aspect")) {
      exitWithError(
          String.format("Incorrect/Missing aspects flag in command args (%s)", aspectFlags));
    }

    ImmutableList.Builder<String> args = ImmutableList.builder();
    args.add(System.getenv("BIT_BAZEL_BINARY"));
    args.add("build");
    args.add("//:foo");
    args.add("--define=ij_product=intellij-latest");
    args.addAll(aspectFlags);
    args.add(
        getOutputGroupsFlag(
            ImmutableList.of(OutputGroup.INFO),
            ImmutableList.of(LanguageClass.GENERIC),
            aspectStrategyBazel));
    ImmutableList<String> command = args.build();

    ProcessBuilder processBuilder =
        new ProcessBuilder()
            .command(command)
            .directory(Paths.get(System.getenv("BIT_WORKSPACE_DIR")).toFile());
    Process bazelInvocation = processBuilder.start();
    int exitCode = bazelInvocation.waitFor();
    String invocationOutput = new String(bazelInvocation.getErrorStream().readAllBytes());

    if (exitCode != 0) {
      exitWithError(
          String.format(
              "Bazel invocation (%s) failed: exit code (%d), invocation result err: (%s) out: (%s).",
              command, exitCode, invocationOutput, new String(bazelInvocation.getInputStream().readAllBytes())));
    }

    // Bazel's output goes into stderr by default, even on success.
    if (!invocationOutput.contains("//:foo up-to-date:")
        || !invocationOutput.contains(getIntelliJInfoTxtFilename("foo"))) {
      exitWithError(String.format("Missing output in invocation result (%s)", invocationOutput));
    }
  }

  private static void exitWithError(String message) {
    System.err.println(message);
    System.exit(1);
  }

  private static String getIntelliJInfoTxtFilename(String targetName) {
    // e.g. foo-97299.intellij-info.txt
    // The hashCode() call here is the implementation function underlying the Starlark hash()
    // function in used in intellij-info-impl.bzl.
    return String.format("%s-%s.intellij-info.txt", targetName, targetName.hashCode());
  }

  private static String getOutputGroupsFlag(
      Collection<OutputGroup> outputGroups,
      Collection<LanguageClass> languageClassList,
      AspectStrategy strategy) {
    // e.g. --output_groups=intellij-info-generic,intellij-resolve-java,intellij-compile-java
    Set<LanguageClass> languageClasses = new HashSet<>(languageClassList);
    String outputGroupNames =
        outputGroups.stream()
            .flatMap(
                g ->
                    strategy
                        .getBaseOutputGroups(g, languageClasses, /* directDepsOnly= */ false)
                        .stream())
            .collect(Collectors.joining(","));
    return "--output_groups=" + outputGroupNames;
  }

  private static BazelVersion getBazelVersion() {
    // The name of the directory containing the bazel binary is formatted as
    // build_bazel_bazel_{major}_{minor}_{bugfix}[-pre] for a bazel binary of version
    // {major}.{minor}.{bugfix}
    String bazelBinaryPath = System.getenv("BIT_BAZEL_BINARY");
    if (bazelBinaryPath == null) {
      return null;
    }
    String bazelDir = new File(bazelBinaryPath).getParentFile().getName();
    // Get the part after last ~ or + (becase of canonicalization with bzlmod)
    if (bazelDir.contains("~")) {
      bazelDir = bazelDir.substring(bazelDir.lastIndexOf("~") + 1);
    } else if (bazelDir.contains("+")) {
      bazelDir = bazelDir.substring(bazelDir.lastIndexOf("+") + 1);
    }
    
    String[] parts = bazelDir.split("_|-");
    if (parts.length < 6) {
      return null;
    }
    try {
      return new BazelVersion(
          Integer.parseInt(parts[3]), Integer.parseInt(parts[4]), Integer.parseInt(parts[5]));
    } catch (NumberFormatException e) {
      // invalid version
      return null;
    }
  }
}
