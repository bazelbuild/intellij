package com.google.idea.blaze.gazelle.sync;

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.bazel.BuildSystem.BuildInvoker;
import com.google.idea.blaze.base.command.BlazeCommand;
import com.google.idea.blaze.base.issueparser.BlazeIssueParser.Parser;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.gazelle.GazelleRunner;
import com.google.idea.blaze.gazelle.GazelleRunResult;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public class MockBlazeGazelleRunnerImpl extends GazelleRunner {

  Optional<BlazeCommand> command = Optional.empty();

  @Override
  public GazelleRunResult runBlazeGazelle(BlazeContext context, BuildInvoker invoker,
      WorkspaceRoot workspaceRoot, List<String> blazeFlags, Label gazelleTarget,
      Collection<WorkspacePath> directories, ImmutableList<Parser> issueParsers) {
    command = Optional.of(
        GazelleRunner.createGazelleCommand(invoker, blazeFlags, gazelleTarget, directories));
    return GazelleRunResult.SUCCESS;
  }

  /**
   * A utility method to clean internal state.
   * Used mainly to reset state between tests.
   */
  public void clean() {
    this.command = Optional.empty();
  }
}
