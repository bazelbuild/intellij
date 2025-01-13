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
import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.base.bazel.BazelVersion;
import com.google.idea.blaze.base.model.BlazeVersionData;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.sync.aspects.storage.AspectRepositoryProvider;
import com.google.idea.blaze.base.sync.aspects.strategy.AspectStrategy.OutputGroup;
import com.google.idea.blaze.base.sync.aspects.strategy.AspectStrategyBazel;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import org.jetbrains.annotations.Contract;

/**
 * A Bazel-invoking integration test for the bundled IntelliJ aspect.
 *
 * <p>These tests assert the end-to-end behavior of the plugin's aspect during a sync, and ensure
 * that it generates the correct IDE info files.
 */
public class BazelInvokingIntegrationTestRunner {

  private static final String ASPECT_DIRECTORY = ".ijwb/aspect";

  public static void main(String[] a) throws Exception {
    final var bazelVersion = getBazelVersion();
    final var projectRoot = getProjectRoot();

    final var aspectDst = projectRoot.resolve(ASPECT_DIRECTORY);

    copyAspects(aspectDst, AspectRepositoryProvider.ASPECT_DIRECTORY);
    copyAspects(aspectDst, AspectRepositoryProvider.ASPECT_TEMPLATE_DIRECTORY);

    ImmutableList.Builder<String> args = ImmutableList.builder();
    args.add(System.getenv("BIT_BAZEL_BINARY"));
    args.add("build");
    args.add("//:foo");
    args.add("--define=ij_product=intellij-latest");
    args.add(String.format("--aspects=//%s:intellij_info_bundled.bzl%%intellij_info_aspect", ASPECT_DIRECTORY));
    args.add(getOutputGroupsFlag(bazelVersion));
    ImmutableList<String> command = args.build();

    ProcessBuilder processBuilder =
        new ProcessBuilder()
            .command(command)
            .directory(projectRoot.toFile());
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

  @Contract("_ -> fail")
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

  private static String getOutputGroupsFlag(BazelVersion version) {
    final var aspectStrategy = new AspectStrategyBazel(
        BlazeVersionData.builder().setBazelVersion(version).build()
    );

    // e.g. --output_groups=intellij-info-generic,intellij-resolve-java,intellij-compile-java
    final var outputGroups = aspectStrategy.getBaseOutputGroups(
        OutputGroup.INFO,
        ImmutableSet.of(LanguageClass.GENERIC),
        /* directDepsOnly= */ false
    );

    return "--output_groups=" + String.join(",", outputGroups);
  }

  private static BazelVersion getBazelVersion() {
    final var bitVersion = System.getenv("BIT_BAZEL_VERSION");
    if (bitVersion == null) {
      exitWithError("Missing environment variable BIT_BAZEL_VERSION");
    }

    final var version = BazelVersion.parseVersion(bitVersion);
    if (version.isAtLeast(999, 0, 0)) {
      exitWithError("Unsupported Bazel version: " + bitVersion);
    }

    return version;
  }

  private static Path getProjectRoot() {
    final var bitWorkspace = System.getenv("BIT_WORKSPACE_DIR");
    if (bitWorkspace == null) {
      exitWithError("Missing environment variable BIT_WORKSPACE_DIR");
    }

    final var root = Path.of(bitWorkspace);
    if (!Files.exists(root)) {
      exitWithError("Project root does not exist: " + root);
    }

    return root;
  }

  private static void copyAspects(Path dst, String src) throws Exception {
    final var classLoader = BazelInvokingIntegrationTestRunner.class.getClassLoader();

    final var srcURL = classLoader.getResource(src);
    if (srcURL == null) {
      throw new IOException("Cannot resolve source");
    }

    final var srcURI = srcURL.toURI();

    // file system needs to be created manually in this case, VFS cannot be used here :(
    try (final var fs = FileSystems.newFileSystem(srcURI, Map.of())) {
      final var srcRoot = Path.of(srcURI);

      try (final var stream = Files.walk(srcRoot)) {
        for (final var iterator = stream.iterator(); iterator.hasNext(); ) {
          final var srcFile = iterator.next();
          final var dstFile = dst.resolve(srcRoot.relativize(srcFile).toString());

          if (Files.isDirectory(srcFile)) {
            Files.createDirectories(dstFile);
          } else {
            Files.write(dstFile, Files.readAllBytes(srcFile), StandardOpenOption.CREATE);
          }
        }
      }
    }
  }
}