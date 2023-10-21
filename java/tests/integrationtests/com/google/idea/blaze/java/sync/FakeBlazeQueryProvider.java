package com.google.idea.blaze.java.sync;

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.dependencies.BlazeQueryDirectoryToTargetProvider;
import com.google.idea.blaze.base.dependencies.TargetInfo;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.sync.projectview.ImportRoots;
import com.google.idea.blaze.base.sync.workspace.WorkspacePathResolver;
import com.intellij.openapi.project.Project;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Need to mock out this target provider since it attempts to execute Bazel outside of the sandbox
 * environment. Used primarily within JavaSyncTest to validate the behavior of the
 * allow_manual_targets_sync: option.
 */
class FakeBlazeQueryProvider extends BlazeQueryDirectoryToTargetProvider {

    private static final String MANUAL_EXCLUDE_TAG = "((?!manual)";

    // Need to override in order to be able to execute getQueryString().
    @Nullable
    @Override
    public List<TargetInfo> doExpandDirectoryTargets(
            Project project,
            Boolean shouldManualTargetSync,
            ImportRoots directories,
            WorkspacePathResolver pathResolver,
            BlazeContext context) {
        return runQuery(project, getQueryString(directories, shouldManualTargetSync), context);
    }

    @Nullable
    private static ImmutableList<TargetInfo> runQuery(Project project, String query, BlazeContext context) {
        if (!query.contains(MANUAL_EXCLUDE_TAG)) {
            TargetInfo targetInfo = TargetInfo.builder(Label.create("//java/com/google:lib"), "java_library").build();
            return ImmutableList.of(targetInfo);
        } else {
            return ImmutableList.of();
        }
    }
}
