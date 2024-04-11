package com.google.idea.blaze.base.sync.workspace;

import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.ideinfo.CIdeInfo;
import com.google.idea.blaze.base.ideinfo.Dependency;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetKey;
import com.google.idea.blaze.base.ideinfo.TargetMap;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.ExecutionRootPath;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.TargetName;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;

import java.io.File;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.Stack;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.jetbrains.annotations.NotNull;
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
  static final Path VIRTUAL_INCLUDES_DIRECTORY = Path.of("_virtual_includes");

  private static final Logger LOG = Logger.getInstance(VirtualIncludesHandler.class);
  private static final String ABSOLUTE_LABEL_PREFIX = "//";
  private static final int EXTERNAL_DIRECTORY_IDX = 3;
  private static final int EXTERNAL_WORKSPACE_NAME_IDX = 4;
  private static final int WORKSPACE_PATH_START_FOR_EXTERNAL_WORKSPACE = 5;
  private static final int WORKSPACE_PATH_START_FOR_LOCAL_WORKSPACE = 3;
  private static final int ABSOLUTE_LABEL_PREFIX_LENGTH = ABSOLUTE_LABEL_PREFIX.length();

  public static boolean useHeuristic() {
    return Registry.is("bazel.sync.resolve.virtual.includes") && !useClangd();
  }

  public static boolean useClangd() {
    return Registry.is("bazel.sync.clangd.virtual.includes");
  }

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

  private static void collectClangdTargetIncludeHints(
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

  private static ImmutableList<String> doCollectClangdIncludeHints(
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

      collectClangdTargetIncludeHints(root, currentKey, currentIdeInfo, resolver, indicator,
          includes);

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
  public static ImmutableList<String> collectClangdIncludeHints(
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
    ImmutableList<String> result = doCollectClangdIncludeHints(projectRoot, targetKey, projectData,
        resolver, indicator);

    long elapsed = stopwatch.elapsed(TimeUnit.MILLISECONDS);
    LOG.info(String.format("Collecting include hints took %dms", elapsed));

    indicator.popState();

    return result;
  }

  private static List<Path> splitExecutionPath(ExecutionRootPath executionRootPath) {
    return Lists.newArrayList(executionRootPath.getAbsoluteOrRelativeFile().toPath().iterator());
  }

  static boolean containsVirtualInclude(ExecutionRootPath executionRootPath) {
    return splitExecutionPath(executionRootPath).contains(VIRTUAL_INCLUDES_DIRECTORY);
  }

  /**
   * Resolves execution root path to {@code _virtual_includes} directory to the matching workspace
   * location
   *
   * @return list of resolved paths if required information is obtained from execution root path and
   * target data or empty list if resolution has failed
   */
  @NotNull
  static ImmutableList<File> resolveVirtualInclude(ExecutionRootPath executionRootPath,
      File externalWorkspacePath,
      WorkspacePathResolver workspacePathResolver,
      TargetMap targetMap) {
    TargetKey key = null;
    try {
      key = guessTargetKey(executionRootPath);
    } catch (IndexOutOfBoundsException exception) {
      // report to intellij EA
      LOG.error(
          "Failed to detect target from execution root path: " + executionRootPath,
          exception);
    }

    if (key == null) {
      return ImmutableList.of();
    }

    TargetIdeInfo info = targetMap.get(key);
    if (info == null) {
      return ImmutableList.of();
    }

    CIdeInfo cIdeInfo = info.getcIdeInfo();
    if (cIdeInfo == null) {
      return ImmutableList.of();
    }

    if (!info.getcIdeInfo().getIncludePrefix().isEmpty()) {
      LOG.debug(
          "_virtual_includes cannot be handled for targets with include_prefix attribute");
      return ImmutableList.of();
    }

    String stripPrefix = info.getcIdeInfo().getStripIncludePrefix();
    if (!stripPrefix.isEmpty()) {
      if (stripPrefix.endsWith("/")) {
        stripPrefix = stripPrefix.substring(0, stripPrefix.length() - 1);
      }
      String externalWorkspaceName = key.getLabel().externalWorkspaceName();
      WorkspacePath stripPrefixWorkspacePath = stripPrefix.startsWith(ABSOLUTE_LABEL_PREFIX) ?
          new WorkspacePath(stripPrefix.substring(ABSOLUTE_LABEL_PREFIX_LENGTH)) :
          new WorkspacePath(key.getLabel().blazePackage(), stripPrefix);
      if (key.getLabel().externalWorkspaceName() != null) {
        ExecutionRootPath external = new ExecutionRootPath(
            ExecutionRootPathResolver.externalPath.toPath()
                .resolve(externalWorkspaceName)
                .resolve(stripPrefixWorkspacePath.toString()).toString());

        return ImmutableList.of(external.getFileRootedAt(externalWorkspacePath));
      } else {
        return workspacePathResolver.resolveToIncludeDirectories(
            stripPrefixWorkspacePath);
      }
    } else {
      return ImmutableList.of();
    }

  }

  /**
   * @throws IndexOutOfBoundsException if executionRootPath has _virtual_includes but its content is
   *                                   unexpected
   */
  @Nullable
  private static TargetKey guessTargetKey(ExecutionRootPath executionRootPath) {
    List<Path> split = splitExecutionPath(executionRootPath);
    int virtualIncludesIdx = split.indexOf(VIRTUAL_INCLUDES_DIRECTORY);

    if (virtualIncludesIdx > -1) {
      String externalWorkspaceName =
          split.get(EXTERNAL_DIRECTORY_IDX)
              .equals(ExecutionRootPathResolver.externalPath.toPath())
              ? split.get(EXTERNAL_WORKSPACE_NAME_IDX).toString()
              : null;

      int workspacePathStart = externalWorkspaceName != null
          ? WORKSPACE_PATH_START_FOR_EXTERNAL_WORKSPACE
          : WORKSPACE_PATH_START_FOR_LOCAL_WORKSPACE;

      List<Path> workspacePaths = (workspacePathStart <= virtualIncludesIdx)
          ? split.subList(workspacePathStart, virtualIncludesIdx)
          : Collections.emptyList();

      String workspacePathString =
          FileUtil.toSystemIndependentName(
              workspacePaths.stream().reduce(Path.of(""), Path::resolve).toString());

      TargetName target = TargetName.create(split.get(virtualIncludesIdx + 1).toString());
      WorkspacePath workspacePath = WorkspacePath.createIfValid(workspacePathString);
      if (workspacePath == null) {
        return null;
      }

      return TargetKey.forPlainTarget(
          Label.create(externalWorkspaceName, workspacePath, target));
    } else {
      return null;
    }
  }
}
