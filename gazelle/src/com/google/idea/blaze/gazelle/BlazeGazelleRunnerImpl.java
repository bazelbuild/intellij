package com.google.idea.blaze.gazelle;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.idea.blaze.base.async.executor.BlazeExecutor;
import com.google.idea.blaze.base.async.process.ExternalTask;
import com.google.idea.blaze.base.async.process.LineProcessingOutputStream;
import com.google.idea.blaze.base.async.process.PrintOutputLineProcessor;
import com.google.idea.blaze.base.bazel.BuildSystem;
import com.google.idea.blaze.base.command.BlazeCommand;
import com.google.idea.blaze.base.command.BlazeCommandName;
import com.google.idea.blaze.base.command.info.BlazeInfoException;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.projectview.section.sections.DirectoryEntry;
import com.google.idea.blaze.base.scope.BlazeContext;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.stream.Collectors;

public class BlazeGazelleRunnerImpl extends BlazeGazelleRunner {
    public ListenableFuture<String> runBlazeGazelle(
            BlazeContext context,
            BuildSystem.BuildInvoker buildInvoker,
            WorkspaceRoot workspaceRoot,
            List<String> blazeFlags,
            Label gazelleTarget,
            List<DirectoryEntry> directories
    ) {
        return BlazeExecutor.getInstance()
                .submit(
                        () -> {
                            String blazeGazelleString = runBlazeGazelle(buildInvoker, workspaceRoot, blazeFlags, context, gazelleTarget, directories)
                                    .toString()
                                    .trim();
                            return blazeGazelleString;
                        });
    }

    private static ByteArrayOutputStream runBlazeGazelle(
            BuildSystem.BuildInvoker buildInvoker,
            WorkspaceRoot workspaceRoot,
            List<String> blazeFlags,
            BlazeContext context,
            Label gazelleTarget,
            List<DirectoryEntry> directories
    ) throws BlazeInfoException {
        BlazeCommand.Builder builder = BlazeCommand.builder(buildInvoker, BlazeCommandName.RUN);
        builder.addBlazeFlags(blazeFlags);
        builder.addTargets(gazelleTarget);
        List<String> directoriesToRegenerate = directories.stream().map(DirectoryEntry::toString).collect(Collectors.toList());
        builder.addExeFlags(directoriesToRegenerate);
        BlazeCommand command = builder.build();
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        int exitCode = ExternalTask.builder(workspaceRoot).addBlazeCommand(command).context(context)
                .stdout(stdout)
                .stderr(stderr)
                .build()
                .run();
        if (exitCode != 0) {
            throw new BlazeInfoException(exitCode, stderr.toString());
        }
        return stdout;
    }

}
