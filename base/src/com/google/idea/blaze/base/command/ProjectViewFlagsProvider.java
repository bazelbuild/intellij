/*
 * Copyright 2025 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.base.command;

import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.projectview.section.sections.BuildFlagsSection;
import com.google.idea.blaze.base.projectview.section.sections.SyncFlagsSection;
import com.google.idea.blaze.base.projectview.section.sections.TestFlagsSection;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.intellij.openapi.project.Project;
import java.util.List;
import java.util.Set;

public class ProjectViewFlagsProvider implements BuildFlagsProvider {

  // bazel info cannot resolve these flags if they reference external repos
  private static final Set<String> INFO_INCOMPATIBLE_FLAGS = Set.of("--platforms", "--config");

  @Override
  public void addBuildFlags(
      Project project,
      ProjectViewSet projectViewSet,
      BlazeCommandName command,
      BlazeInvocationContext invocationContext,
      List<String> flags) {
    // most flags cannot be used with bazel mod (#6756)
    if (BlazeCommandName.MOD.equals(command)) {
      return;
    }

    // some build configuration flags can cause the bazel info command to fail
    if (BlazeCommandName.INFO.equals(command)) {
      return;
    }

    flags.addAll(BlazeFlags.expandBuildFlags(projectViewSet.listItems(BuildFlagsSection.KEY)));

    if (BlazeCommandName.TEST.equals(command)) {
      flags.addAll(BlazeFlags.expandBuildFlags(projectViewSet.listItems(TestFlagsSection.KEY)));
    }
  }

  @Override
  public void addSyncFlags(
      Project project,
      ProjectViewSet projectViewSet,
      BlazeCommandName command,
      BlazeContext context,
      BlazeInvocationContext invocationContext,
      List<String> flags) {
    final var syncFlags = BlazeFlags.expandBuildFlags(projectViewSet.listItems(SyncFlagsSection.KEY));

    if (BlazeCommandName.INFO.equals(command)) {
      removeInfoIncompatibleFlags(syncFlags);
    }

    flags.addAll(syncFlags);
  }

  private static void removeInfoIncompatibleFlags(List<String> flags) {
    final var iter = flags.listIterator();

    while (iter.hasNext()) {
      final var flag = iter.next();

      final var name = flag.split("[=\\s]", 2)[0];
      if (!INFO_INCOMPATIBLE_FLAGS.contains(name)) {
        continue;
      }

      iter.remove();

      // a bare flag carries its value in the next argument; drop that too
      if (flag.equals(name) && iter.hasNext()) {
        iter.next();
        iter.remove();
      }
    }
  }
}
