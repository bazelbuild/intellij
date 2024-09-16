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

  static boolean useHeuristic() {
    return Registry.is("bazel.sync.resolve.virtual.includes");
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
      if (externalWorkspaceName != null) {
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
