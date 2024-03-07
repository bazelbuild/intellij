package com.google.idea.blaze.base.sync.workspace;

import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.ideinfo.CIdeInfo;
import com.google.idea.blaze.base.ideinfo.Dependency;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetKey;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.ExecutionRootPath;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.registry.Registry;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.Stack;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.Nullable;

/**
 * Utility class designed to convert execroot {@code _virtual_includes} references to
 * either external or local workspace.
 * <p>
 * Virtual includes are generated for targets with strip_include_prefix attribute
 * and are stored for external workspaces in
 * <p>
 * {@code bazel-out/.../bin/external/.../_virtual_includes/...}
 * <p>
 * or for local workspace in
 * <p>
 * {@code bazel-out/.../bin/.../_virtual_includes/...}
 */
public class VirtualIncludesHandler {
  private static final Logger LOG = Logger.getInstance(VirtualIncludesHandler.class);
  private static final String ABSOLUTE_LABEL_PREFIX = "//";

  private static Path trimStart(Path value, @Nullable Path prefix) {
    if (prefix == null || !value.startsWith(prefix)) {
      return value;
    }

    return value.subpath(prefix.getNameCount(), value.getNameCount());
  }

  @Nullable
  private static Path pathOf(@Nullable String value) {
    if (value == null || value.isEmpty()) {
      return null;
    }

    // turns a windows absolut path into a normal absolut path
    if (value.startsWith(ABSOLUTE_LABEL_PREFIX)) {
      value = value.substring(1);
    }

    try {
      return Path.of(value);
    } catch (InvalidPathException e) {
      LOG.warn("invalid path: " + value);
      return null;
    }
  }

  private static void collectTargetIncludeHints(
      Path root,
      TargetKey targetKey,
      TargetIdeInfo targetIdeInfo,
      ExecutionRootPathResolver resolver,
      ProgressIndicator indicator,
      ImmutableList.Builder<String> includes) {
    CIdeInfo cIdeInfo = targetIdeInfo.getcIdeInfo();
    if (cIdeInfo == null) {
      return;
    }

    Path includePrefix = pathOf(cIdeInfo.getIncludePrefix());
    Path stripPrefix = pathOf(cIdeInfo.getStripIncludePrefix());
    Path packagePath = targetKey.getLabel().blazePackage().asPath();

    String externalWorkspaceName = targetKey.getLabel().externalWorkspaceName();

    for (ArtifactLocation header : cIdeInfo.getHeaders()) {
      indicator.checkCanceled();

      Path realPath;
      if (externalWorkspaceName != null) {
        Path externalWorkspace = ExecutionRootPathResolver.externalPath.toPath()
            .resolve(externalWorkspaceName);

        Path resolvedPath = resolver.resolveToExternalWorkspaceWithSymbolicLinkResolution(
            new ExecutionRootPath(externalWorkspace.toString())).get(0).toPath();

        realPath = resolvedPath.resolve(header.getRelativePath());
      } else {
        realPath = root.resolve(header.getExecutionRootRelativePath());
      }

      Path codePath = pathOf(header.getRelativePath());
      if (codePath == null) {
        continue;
      }

      // if the path is absolut, strip prefix is a repository-relative path
      if (stripPrefix != null && stripPrefix.isAbsolute()) {
        codePath = trimStart(codePath, stripPrefix.subpath(0, stripPrefix.getNameCount()));
      }

      codePath = trimStart(codePath, packagePath);

      // if the path is not absolut, strip prefix is a package-relative path
      if (stripPrefix != null && !stripPrefix.isAbsolute()) {
        codePath = trimStart(codePath, stripPrefix);
      }

      if (includePrefix != null) {
        codePath = includePrefix.resolve(codePath);
      }

      includes.add(String.format("-ibazel%s=%s", codePath, realPath));
    }
  }

  private static ImmutableList<String> doCollectIncludeHints(
      Path root,
      TargetKey targetKey,
      BlazeProjectData projectData,
      ExecutionRootPathResolver resolver,
      ProgressIndicator indicator) {
    Stack<TargetKey> frontier = new Stack<>();
    frontier.push(targetKey);

    Set<TargetKey> explored = new HashSet<>();

    ImmutableList.Builder<String> includes = ImmutableList.builder();
    while (!frontier.isEmpty()) {
      indicator.checkCanceled();

      TargetKey currentKey = frontier.pop();
      if (!explored.add(currentKey)) {
        continue;
      }

      TargetIdeInfo currentIdeInfo = projectData.getTargetMap().get(currentKey);
      if (currentIdeInfo == null) {
        continue;
      }

      collectTargetIncludeHints(root, currentKey, currentIdeInfo, resolver, indicator, includes);

      for (Dependency dep : currentIdeInfo.getDependencies()) {
        frontier.push(dep.getTargetKey());
      }
    }

    return includes.build();
  }

  /**
   * Collects all header files form the targetKey and its dependencies to create the '-ibazel'
   * mappings. The mappings are used to resolve headers which use an 'include_prefix' or a
   * 'strip_include_prefix'.
   */
  public static ImmutableList<String> collectIncludeHints(
      Path projectRoot,
      TargetKey targetKey,
      BlazeProjectData projectData,
      ExecutionRootPathResolver resolver,
      ProgressIndicator indicator) {
    if (Registry.is("bazel.cpp.sync.workspace.collect.include.prefix.hints.disabled")) {
      return ImmutableList.of();
    }

    indicator.pushState();
    indicator.setIndeterminate(true);
    indicator.setText2("Collecting include hints...");

    Stopwatch stopwatch = Stopwatch.createStarted();
    ImmutableList<String> result = doCollectIncludeHints(projectRoot, targetKey, projectData,
        resolver, indicator);

    long elapsed = stopwatch.elapsed(TimeUnit.MILLISECONDS);
    LOG.info(String.format("Collecting include hints took %dms", elapsed));

    indicator.popState();

    return result;
  }
}
