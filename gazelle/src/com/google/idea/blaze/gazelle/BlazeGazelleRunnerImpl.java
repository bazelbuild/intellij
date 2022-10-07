package com.google.idea.blaze.gazelle;

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.async.process.ExternalTask;
import com.google.idea.blaze.base.async.process.LineProcessingOutputStream;
import com.google.idea.blaze.base.bazel.BuildSystem.BuildInvoker;
import com.google.idea.blaze.base.command.BlazeCommand;
import com.google.idea.blaze.base.command.BlazeCommandName;
import com.google.idea.blaze.base.issueparser.BlazeIssueParser.Parser;
import com.google.idea.blaze.base.issueparser.IssueOutputLineProcessor;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.scope.BlazeContext;
import java.io.ByteArrayOutputStream;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public class BlazeGazelleRunnerImpl extends BlazeGazelleRunner {

  @Override
  public GazelleRunResult runBlazeGazelle(
      BlazeContext context,
      BuildInvoker invoker,
      WorkspaceRoot workspaceRoot,
      List<String> blazeFlags,
      Label gazelleTarget,
      Collection<WorkspacePath> directories,
      ImmutableList<Parser> issueParsers) {
    BlazeCommand.Builder builder = BlazeCommand.builder(invoker, BlazeCommandName.RUN);
    builder.addBlazeFlags(blazeFlags);
    builder.addTargets(gazelleTarget);
    List<String> directoriesToRegenerate =
        directories.stream().map(WorkspacePath::toString).collect(Collectors.toList());
    builder.addExeFlags(directoriesToRegenerate);
    BlazeCommand command = builder.build();
    ByteArrayOutputStream stdout = new ByteArrayOutputStream();
    int exitCode =
        ExternalTask.builder(workspaceRoot)
            .addBlazeCommand(command)
            .context(context)
            .stderr(
                LineProcessingOutputStream.of(new IssueOutputLineProcessor(context, issueParsers)))
            .stdout(stdout)
            .build()
            .run();
    if (exitCode != 0) {
      // Note that gazelle won't return a non-0 exit code on a failure unless we specify the -strict
      // flag.
      // This exception will only catch the instances of bazel failing to build the gazelle target.
      return GazelleRunResult.FAILED_TO_RUN;
    }
    // If the issue parsers have caught at least one error, they will have modified the context.
    return context.hasErrors() ? GazelleRunResult.RAN_WITH_ERRORS : GazelleRunResult.SUCCESS;
  }
}
