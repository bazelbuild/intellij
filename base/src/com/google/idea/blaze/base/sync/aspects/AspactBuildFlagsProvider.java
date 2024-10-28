package com.google.idea.blaze.base.sync.aspects;

import com.google.idea.blaze.base.command.BlazeCommandName;
import com.google.idea.blaze.base.command.BlazeInvocationContext;
import com.google.idea.blaze.base.command.BuildFlagsProvider;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.sync.aspects.strategy.AspectRepositoryProvider;
import com.intellij.openapi.project.Project;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public class AspactBuildFlagsProvider implements BuildFlagsProvider {

  @Override
  public void addBuildFlags(
      Project project,
      ProjectViewSet projectViewSet,
      BlazeCommandName command,
      BlazeInvocationContext invocationContext,
      List<String> flags) {
    // aspects are only required during sync
  }

  @Override
  public void addSyncFlags(
      Project project,
      ProjectViewSet projectViewSet,
      BlazeCommandName command,
      BlazeContext context,
      BlazeInvocationContext invocationContext,
      List<String> flags) {
    Arrays.stream(AspectRepositoryProvider.getOverrideFlags(project)).filter(Optional::isPresent)
        .map(Optional::get)
        .forEach(flags::add);
  }
}
