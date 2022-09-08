package com.google.idea.blaze.gazelle;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.idea.blaze.base.bazel.BuildSystem;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.projectview.section.sections.DirectoryEntry;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.intellij.openapi.components.ServiceManager;

import java.util.List;

/** Runs the blaze run command, on gazelle. The results may be cached in the workspace. */
public abstract class BlazeGazelleRunner {
    public static BlazeGazelleRunner getInstance() {
        return ServiceManager.getService(BlazeGazelleRunner.class);
    }

    /**
     * This will call Blaze run with the argument of the gazelle target provided.
     *
     * @param blazeFlags The blaze flags that will be passed to Blaze.
     * @return stdout of the gazelle process.
     */
    public abstract ListenableFuture<String> runBlazeGazelle(
            BlazeContext context,
            BuildSystem.BuildInvoker invoker,
            WorkspaceRoot workspaceRoot,
            List<String> blazeFlags,
            Label gazelleTarget,
            List<DirectoryEntry> directories
    );
}
