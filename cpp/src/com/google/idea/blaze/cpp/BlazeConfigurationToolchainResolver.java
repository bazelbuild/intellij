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
package com.google.idea.blaze.cpp;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static java.util.stream.Collectors.joining;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.devtools.intellij.ideinfo.IntellijIdeInfo.Dependency.DependencyType;
import com.google.idea.blaze.base.async.executor.BlazeExecutor;
import com.google.idea.blaze.base.ideinfo.CToolchainIdeInfo;
import com.google.idea.blaze.base.ideinfo.Dependency;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetKey;
import com.google.idea.blaze.base.ideinfo.TargetMap;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.ExecutionRootPath;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.Scope;
import com.google.idea.blaze.base.scope.output.IssueOutput;
import com.google.idea.blaze.base.scope.scopes.TimingScope;
import com.google.idea.blaze.base.scope.scopes.TimingScope.EventType;
import com.google.idea.blaze.base.sync.workspace.ExecutionRootPathResolver;
import com.google.idea.blaze.cpp.CompilerVersionChecker.VersionCheckException;
import com.google.idea.blaze.cpp.environment.EnvironmentProcessor;
import com.google.idea.blaze.cpp.XcodeCompilerSettingsProvider.XcodeCompilerSettingsException;
import com.intellij.aspect.lib.ReadtargetKt;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;

import java.io.File;
import java.io.IOException;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import javax.annotation.Nullable;

/**
 * Converts {@link CToolchainIdeInfo} to interfaces used by {@link
 * com.jetbrains.cidr.lang.workspace.OCResolveConfiguration}
 */
public final class BlazeConfigurationToolchainResolver {

  // placeholders that the apple_support compiler wrapper replaces
  private static final String EXECUTION_ROOT_PLACEHOLDER = "__BAZEL_EXECUTION_ROOT__";
  private static final String EXECUTION_ROOT_CANONICAL_PLACEHOLDER = "__BAZEL_EXECUTION_ROOT_CANONICAL__";
  private static final String XCODE_DEVELOPER_DIR_PLACEHOLDER = "__BAZEL_XCODE_DEVELOPER_DIR__";
  private static final String XCODE_SDKROOT_PLACEHOLDER = "__BAZEL_XCODE_SDKROOT__";

  private BlazeConfigurationToolchainResolver() {}

  /** Returns the C toolchain used by each C target */
  @VisibleForTesting
  public static ImmutableMap<TargetKey, CToolchainIdeInfo> buildToolchainLookupMap(
      BlazeContext context,
      TargetMap targetMap
  ) {
    return Scope.push(context, childContext -> {
      childContext.push(new TimingScope("Build toolchain lookup map", EventType.Other));

      final var toolchains = targetMap.targets().stream()
          .filter(target -> target.getcToolchainIdeInfo() != null)
          .collect(toImmutableMap(TargetIdeInfo::getKey, TargetIdeInfo::getcToolchainIdeInfo));

      final var toolchainDepsTable = buildToolchainDepsTable(targetMap.targets(), toolchains);
      verifyToolchainDeps(context, toolchainDepsTable);

      return buildLookupTable(toolchainDepsTable, toolchains);
    });
  }

  private static ImmutableMap<TargetIdeInfo, List<TargetKey>> buildToolchainDepsTable(
      ImmutableCollection<TargetIdeInfo> targets,
      Map<TargetKey, CToolchainIdeInfo> toolchains
  ) {
    final var toolchainDepsTable = ImmutableMap.<TargetIdeInfo, List<TargetKey>>builder();
    for (final var target : targets) {
      // switch to: target.getcIdeInfo() == null || target.getcToolchainIdeInfo() != null
      // once the IntelliJ aspect becomes the default (#8412) as it can handle proto correctly
      if (!target.getKind().hasLanguage(LanguageClass.C)  || target.getcToolchainIdeInfo() != null) {
        continue;
      }

      final var toolchainCandidates = target.getDependencies().stream()
          .filter(it -> it.getDependencyType() == DependencyType.TOOLCHAIN)
          .map(Dependency::getTargetKey)
          .filter(toolchains::containsKey)
          .collect(toImmutableList());

      if (!toolchainCandidates.isEmpty()) {
        toolchainDepsTable.put(target, toolchainCandidates);
        continue;
      }

      final var fallbackCandidates = target.getDependencies().stream()
          .map(Dependency::getTargetKey)
          .filter(toolchains::containsKey)
          .collect(toImmutableList());

      toolchainDepsTable.put(target, fallbackCandidates);
    }

    return toolchainDepsTable.build();
  }

  private static ImmutableMap<TargetKey, CToolchainIdeInfo> buildLookupTable(
      ImmutableMap<TargetIdeInfo, List<TargetKey>> toolchainDepsTable,
      ImmutableMap<TargetKey, CToolchainIdeInfo> toolchains
  ) {
    final var lookupTable = ImmutableMap.<TargetKey, CToolchainIdeInfo>builder();
    for (final var entry : toolchainDepsTable.entrySet()) {
      final var target = entry.getKey();
      final var toolchainDeps = entry.getValue();

      if (!toolchainDeps.isEmpty()) {
        // pick the first toolchain the target depends on
        final var toolchainKey = toolchainDeps.get(0);
        final var toolchainInfo = toolchains.get(toolchainKey);
        lookupTable.put(target.getKey(), toolchainInfo);
      } else {
        // pick an arbitrary toolchain
        final var arbitraryToolchain = Iterables.getFirst(toolchains.values(), null);
        if (arbitraryToolchain != null) {
          lookupTable.put(target.getKey(), arbitraryToolchain);
        }
      }
    }

    return lookupTable.build();
  }

  private static void verifyToolchainDeps(
      BlazeContext context,
      ImmutableMap<TargetIdeInfo, List<TargetKey>> toolchainDepsTable
  ) {
    final var invalidTargets = toolchainDepsTable.entrySet()
        .stream()
        .filter(deps -> deps.getValue().size() != 1)
        .toList();

    if (invalidTargets.isEmpty()) {
      return;
    }

    final var builder = new StringBuilder();
    for (final var target : invalidTargets) {
      builder.append(target.getKey().getKey().presentable());
      builder.append(": ");

      if (target.getValue().isEmpty()) {
        builder.append("No toolchain dependencies found");
      } else {
        builder.append(target.getValue().stream().map(TargetKey::presentable).collect(joining(", ")));
      }
      builder.append("\n");
    }

    IssueOutput.warn("Unexpected number of cc toolchain dependencies")
        .withDescription(builder.toString())
        .submit(context);
  }

  /**
   * Returns the compiler settings for each toolchain.
   */
  static ImmutableMap<CToolchainIdeInfo, BlazeCompilerSettings> buildCompilerSettingsMap(
      BlazeContext context,
      Project project,
      ImmutableMap<TargetKey, CToolchainIdeInfo> toolchainLookupMap,
      ExecutionRootPathResolver executionRootPathResolver,
      ImmutableMap<CToolchainIdeInfo, BlazeCompilerSettings> oldCompilerSettings,
      Optional<XcodeCompilerSettings> xcodeCompilerSettings
  ) {
    return Scope.push(context, childContext -> {
      childContext.push(new TimingScope("Build compiler settings map", EventType.Other));

      return doBuildCompilerSettingsMap(
          context,
          project,
          toolchainLookupMap,
          executionRootPathResolver,
          xcodeCompilerSettings,
          oldCompilerSettings
      );
    });
  }

  private static @Nullable File resolveCompilerExecutable(
      BlazeContext context,
      ExecutionRootPathResolver executionRootPathResolver,
      ExecutionRootPath compilerPath
  ) {
    File compilerFile = executionRootPathResolver.resolveExecutionRootPath(compilerPath);

    if (compilerFile == null) {
      IssueOutput.error("Unable to find compiler executable: " + compilerPath).submit(context);
      return null;
    }

    if (!compilerFile.exists() && SystemInfo.isWindows) {
      // bazel reports the compiler executable without the exe suffix
      compilerFile = new File(compilerFile.getAbsolutePath() + ".exe");
    }

    return compilerFile;
  }

  private static @Nullable String mergeCompilerVersions(
      BlazeContext context,
      @Nullable String cVersion,
      @Nullable String cppVersion) {
    if (cVersion == null) {
      return cppVersion;
    }
    if (cppVersion == null) {
      return cVersion;
    }
    if (cVersion.equals(cppVersion)) {
      return cppVersion;
    }

    IssueOutput.warn("C and Cpp compiler version mismatch. Defaulting to Cpp compiler version.")
        .submit(context);

    return cppVersion;
  }

  private static ImmutableMap<CToolchainIdeInfo, BlazeCompilerSettings> doBuildCompilerSettingsMap(
      BlazeContext context,
      Project project,
      ImmutableMap<TargetKey, CToolchainIdeInfo> toolchainLookupMap,
      ExecutionRootPathResolver executionRootPathResolver,
      Optional<XcodeCompilerSettings> xcodeCompilerSettings,
      ImmutableMap<CToolchainIdeInfo, BlazeCompilerSettings> oldCompilerSettings
  ) {
    final var toolchains = new HashSet<>(toolchainLookupMap.values());
    final var compilerSettingsFutures = new ArrayList<ListenableFuture<Map.Entry<CToolchainIdeInfo, BlazeCompilerSettings>>>();

    for (CToolchainIdeInfo toolchain : toolchains) {
      compilerSettingsFutures.add(submit(() -> doBuildCompilerSettings(
          context,
          project,
          toolchain,
          executionRootPathResolver,
          xcodeCompilerSettings,
          oldCompilerSettings
      )));
    }

    final var compilerSettingsMap = ImmutableMap.<CToolchainIdeInfo, BlazeCompilerSettings>builder();
    try {
      Futures.allAsList(compilerSettingsFutures).get().stream()
          .filter(Objects::nonNull)
          .forEach(compilerSettingsMap::put);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      context.setCancelled();
    } catch (ExecutionException e) {
      IssueOutput.error("Could not build C compiler settings map: " + e).submit(context);
    }

    return compilerSettingsMap.build();
  }

  private static @Nullable Map.Entry<CToolchainIdeInfo, BlazeCompilerSettings> doBuildCompilerSettings(
      BlazeContext context,
      Project project,
      CToolchainIdeInfo toolchain,
      ExecutionRootPathResolver executionRootPathResolver,
      Optional<XcodeCompilerSettings> fallbackXcodeSettings,
      ImmutableMap<CToolchainIdeInfo, BlazeCompilerSettings> oldCompilerSettings
  ) {
    // prefer the Xcode info reported by the toolchain, fall back to the project wide settings otherwise
    final var xcodeSettings = resolveToolchainXcodeSettings(context, executionRootPathResolver, toolchain)
        .or(() -> fallbackXcodeSettings);

    final var cCompiler = resolveCompilerExecutable(context, executionRootPathResolver, toolchain.cCompiler());
    if (cCompiler == null) {
      return null;
    }
    final var cppCompiler = resolveCompilerExecutable(context, executionRootPathResolver, toolchain.cppCompiler());
    if (cppCompiler == null) {
      return null;
    }

    final var cEnvironment = EnvironmentProcessor.apply(toolchain.cEnvironment(), executionRootPathResolver);
    final var cppEnvironment = EnvironmentProcessor.apply(toolchain.cppEnvironment(), executionRootPathResolver);

    final var cCompilerVersion = getCompilerVersion(
        project,
        context,
        executionRootPathResolver,
        xcodeSettings,
        cEnvironment,
        cCompiler
    );
    final var cppCompilerVersion = getCompilerVersion(
        project,
        context,
        executionRootPathResolver,
        xcodeSettings,
        cppEnvironment,
        cppCompiler
    );

    var compilerVersion = mergeCompilerVersions(context, cCompilerVersion, cppCompilerVersion);
    if (compilerVersion == null) {
      compilerVersion = CompilerVersionUtil.FALLBACK_VERSION;
    }

    final var oldSettings = oldCompilerSettings.get(toolchain);
    if (oldSettings != null && oldSettings.version().equals(compilerVersion)) {
      return new SimpleImmutableEntry<>(toolchain, oldSettings);
    }

    final var xcodeEnvironment = XcodeCompilerSettingsProvider
        .getInstance(project)
        .asEnvironmentVariables(xcodeSettings);

    final var environment = mergeEnvironments(
        xcodeEnvironment,
        cEnvironment,
        cppEnvironment
    );

    final var placeholders = createPlaceholders(executionRootPathResolver.getExecutionRoot(), xcodeSettings);

    final var settings = BlazeCompilerSettings.builder()
        .setCCompiler(cCompiler)
        .setCppCompiler(cppCompiler)
        .setCSwitches(expandPlaceholders(toolchain.cCompilerOptions(), placeholders))
        .setCppSwitches(expandPlaceholders(toolchain.cppCompilerOptions(), placeholders))
        .setVersion(compilerVersion)
        .setEnvironment(environment)
        .setBuiltInIncludes(toolchain.builtInIncludeDirectories())
        .setName(toolchain.compilerName())
        .setSysroot(toolchain.sysroot())
        .build();

    if (settings == null) {
      return null;
    }

    return new SimpleImmutableEntry<>(toolchain, settings);
  }

  /**
   * Reads the resolved Xcode environment generated by the aspect for Apple toolchains. Only available with the IntelliJ
   * aspect, returns empty for toolchains without Xcode info.
   */
  private static Optional<XcodeCompilerSettings> resolveToolchainXcodeSettings(
      BlazeContext context,
      ExecutionRootPathResolver executionRootPathResolver,
      CToolchainIdeInfo toolchain
  ) {
    final var xcodeInfo = toolchain.xcodeInfo();
    if (xcodeInfo == null) {
      return Optional.empty();
    }

    final var file = executionRootPathResolver.resolveExecutionRootPath(
        ExecutionRootPath.create(xcodeInfo.getExecutionRootRelativePath())
    );

    final var environment = file != null ? ReadtargetKt.readEnvFromFile(file.toPath()) : null;
    if (environment == null) {
      IssueOutput.warn("Unable to read Xcode info of toolchain: " + xcodeInfo).submit(context);
      return Optional.empty();
    }

    return XcodeCompilerSettings.fromEnvironment(environment);
  }

  private static ImmutableMap<String, String> createPlaceholders(
      File executionRoot,
      Optional<XcodeCompilerSettings> xcodeSettings
  ) {
    File canonicalExecutionRoot;
    try {
      canonicalExecutionRoot = executionRoot.toPath().toRealPath().toFile();
    } catch (IOException e) {
      canonicalExecutionRoot = executionRoot;
    }

    final var placeholders = ImmutableMap.<String, String>builder()
        .put(EXECUTION_ROOT_PLACEHOLDER, executionRoot.toString())
        .put(EXECUTION_ROOT_CANONICAL_PLACEHOLDER, canonicalExecutionRoot.toString());

    xcodeSettings.ifPresent(settings -> placeholders
        .put(XCODE_DEVELOPER_DIR_PLACEHOLDER, settings.getDeveloperDir().toString())
        .put(XCODE_SDKROOT_PLACEHOLDER, settings.getSdkRoot().toString()));

    return placeholders.build();
  }

  private static ImmutableList<String> expandPlaceholders(
      ImmutableList<String> options,
      ImmutableMap<String, String> placeholders
  ) {
    return options.stream().map(option -> {
      for (final var placeholder : placeholders.entrySet()) {
        option = option.replace(placeholder.getKey(), placeholder.getValue());
      }
      return option;
    }).collect(toImmutableList());
  }

  @SafeVarargs
  private static ImmutableMap<String, String> mergeEnvironments(Map<String, String>... environments) {
    final var merged = new LinkedHashMap<String, String>();
    for (final var environment : environments) {
      merged.putAll(environment);
    }
    return ImmutableMap.copyOf(merged);
  }

  @Nullable
  private static String getCompilerVersion(
      Project project,
      BlazeContext context,
      ExecutionRootPathResolver executionRootPathResolver,
      Optional<XcodeCompilerSettings> xcodeCompilerSettings,
      Map<String, String> toolchainEnv,
      File executable) {
    final var executionRoot = executionRootPathResolver.getExecutionRoot();

    final var xcodeEnvFlags = XcodeCompilerSettingsProvider.getInstance(project)
        .asEnvironmentVariables(xcodeCompilerSettings);
    final var compilerEnvFlags = mergeEnvironments(xcodeEnvFlags, toolchainEnv);

    try {
      return CompilerVersionChecker.getInstance().checkCompilerVersion(executionRoot, executable, compilerEnvFlags);
    } catch (VersionCheckException e) {
      final var builder = new StringBuilder();

      switch (e.kind) {
        case MISSING_EXEC_ROOT:
          builder.append("Missing execution root:\n").append(executionRoot.getAbsolutePath()).append("\n");
          break;
        case MISSING_COMPILER:
          builder.append("Missing compiler executable:\n").append(executable.getAbsolutePath()).append("\n");
          break;
        case GENERIC_FAILURE:
          builder.append("Failed to check compiler version:\n").append(e.getMessage()).append("\n");
          builder.append("Compiler executable:\n").append(executable.getAbsolutePath()).append("\n");
          break;
      }

      IssueOutput.warn("Unable to check compiler version").withDescription(builder.toString()).submit(context);
      return null;
    }
  }

  private static <T> ListenableFuture<T> submit(Callable<T> callable) {
    return BlazeExecutor.getInstance().submit(callable);
  }

  public static Optional<XcodeCompilerSettings> resolveXcodeCompilerSettings(BlazeContext context, Project project,
      BlazeProjectData projectData) {
    return Scope.push(
        context,
        childContext -> {
          childContext.push(new TimingScope("Resolve Xcode information", EventType.Other));
          try {
            return XcodeCompilerSettingsProvider.getInstance(project).fromContext(context, project, projectData);
          } catch (XcodeCompilerSettingsException e) {
            IssueOutput.warn(
                String.format(
                    "There was an error fetching the Xcode information from the build: %s\n\nSome C++ functionality may not be available.",
                    e.toString())
            ).submit(childContext);
            return Optional.empty();
          }
        });
  }

  public static ImmutableMap<TargetKey, BlazeCompilerSettings> buildCompilerLookupMap(
      ImmutableMap<TargetKey, CToolchainIdeInfo> toolchainLookupMap,
      ImmutableMap<CToolchainIdeInfo, BlazeCompilerSettings> compilerSettings
  ) {
    final var builder = ImmutableMap.<TargetKey, BlazeCompilerSettings>builder();
    for (final var entry : toolchainLookupMap.entrySet()) {
      final var settings = compilerSettings.get(entry.getValue());
      if (settings != null) {
        builder.put(entry.getKey(), settings);
      }
    }

    return builder.build();
  }
}
