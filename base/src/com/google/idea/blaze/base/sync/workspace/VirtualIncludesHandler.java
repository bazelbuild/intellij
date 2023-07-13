package com.google.idea.blaze.base.sync.workspace;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.idea.blaze.base.ideinfo.CIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetKey;
import com.google.idea.blaze.base.ideinfo.TargetMap;
import com.google.idea.blaze.base.model.primitives.ExecutionRootPath;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.TargetName;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

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
class VirtualIncludesHandler {
    final static Path VIRTUAL_INCLUDES_DIRECTORY = Path.of("_virtual_includes");
    private static final int EXTERNAL_DIRECTORY_IDX = 3;
    private static final int EXTERNAL_WORKSPACE_NAME_IDX = 4;
    private static final int WORKSPACE_PATH_START_FOR_EXTERNAL_WORKSPACE = 5;
    private static final int WORKSPACE_PATH_START_FOR_LOCAL_WORKSPACE = 3;

    private static List<Path> splitExecutionPath(ExecutionRootPath executionRootPath) {
        return Lists.newArrayList(executionRootPath.getAbsoluteOrRelativeFile().toPath().iterator());
    }

    static boolean containsVirtualInclude(ExecutionRootPath executionRootPath) {
        return splitExecutionPath(executionRootPath).contains(VIRTUAL_INCLUDES_DIRECTORY);
    }

    /**
     * Resolves execution root path to {@code _virtual_includes} directory to the matching workspace location
     *
     * @return list of resolved paths if required information is obtained from execution root path and target data
     * or empty list if resolution has failed
     */
    @NotNull
    static ImmutableList<File> resolveVirtualInclude(ExecutionRootPath executionRootPath,
        File externalWorkspacePath,
        WorkspacePathResolver workspacePathResolver,
        TargetMap targetMap)
    {
        TargetKey key = guessTargetKey(executionRootPath);
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

        String stripPrefix = info.getcIdeInfo().getStripIncludePrefix();
        if (!stripPrefix.isEmpty()) {
            String extrenalWorkspaceName = key.getLabel().externalWorkspaceName();
            WorkspacePath workspacePath = key.getLabel().blazePackage();
            if (key.getLabel().externalWorkspaceName() != null) {
                ExecutionRootPath external = new ExecutionRootPath(
                    ExecutionRootPathResolver.externalPath.toPath()
                        .resolve(extrenalWorkspaceName)
                        .resolve(workspacePath.toString())
                        .resolve(stripPrefix).toString());

                return ImmutableList.of(external.getFileRootedAt(externalWorkspacePath));
            } else {
                return workspacePathResolver.resolveToIncludeDirectories(
                    new WorkspacePath(workspacePath, stripPrefix));
            }
        } else {
            return ImmutableList.of();
        }

    }

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

            String workspacePathString = workspacePaths.stream().reduce(Path.of(""), Path::resolve)
                .toString();

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
