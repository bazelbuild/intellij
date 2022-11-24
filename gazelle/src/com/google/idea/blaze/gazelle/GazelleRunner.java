package com.google.idea.blaze.gazelle;

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.bazel.BuildSystem.BuildInvoker;
import com.google.idea.blaze.base.command.BlazeCommand;
import com.google.idea.blaze.base.command.BlazeCommandName;
import com.google.idea.blaze.base.issueparser.BlazeIssueParser.Parser;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.intellij.openapi.application.ApplicationManager;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/** Runs the blaze run command, on gazelle. The results may be cached in the workspace. */
public abstract class GazelleRunner {

  public static GazelleRunner getInstance() {
    return ApplicationManager.getApplication().getService(GazelleRunner.class);
  }

  protected static BlazeCommand createGazelleCommand(
      BuildInvoker invoker,
      List<String> blazeFlags,
      Label gazelleTarget,
      Collection<WorkspacePath> directories
  ) {
    BlazeCommand.Builder builder = BlazeCommand.builder(invoker, BlazeCommandName.RUN);
    builder.addBlazeFlags(blazeFlags);
    builder.addTargets(gazelleTarget);
    List<String> directoriesToRegenerate =
        directories.stream().map(WorkspacePath::toString).collect(Collectors.toList());
    builder.addExeFlags(directoriesToRegenerate);
    return builder.build();
  }

  /**
   * Run the provided Gazelle target via Blaze.
   *
   * @return GazelleRunResult,
   *         an enum determining whether the command succeeded, failed, and/or had errors.
   */
  public abstract GazelleRunResult runBlazeGazelle(
      BlazeContext context,
      BuildInvoker invoker,
      WorkspaceRoot workspaceRoot,
      List<String> blazeFlags,
      Label gazelleTarget,
      Collection<WorkspacePath> directories,
      ImmutableList<Parser> issueParsers);
}
