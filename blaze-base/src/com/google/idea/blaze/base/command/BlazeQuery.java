/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
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

import com.google.idea.blaze.base.async.process.ExternalTask;
import com.google.idea.blaze.base.async.process.LineProcessingOutputStream;
import com.google.idea.blaze.base.issueparser.IssueOutputLineProcessor;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.BlazeImportSettings;
import com.google.idea.blaze.base.settings.BlazeImportSettingsManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.io.OutputStream;

/**
 * Code for issuing a blaze query.
 */
public class BlazeQuery {

  public static void query(
    @NotNull final Project project,
    @NotNull BlazeContext context,
    @NotNull final String query,
    @NotNull OutputStream stdout) {
    BlazeImportSettings importSettings = BlazeImportSettingsManager.getInstance(project)
      .getImportSettings();
    assert importSettings != null;
    final WorkspaceRoot workspaceRoot = WorkspaceRoot.fromImportSettings(importSettings);

    final BlazeCommand command = BlazeCommand.builder(Blaze.getBuildSystem(project), BlazeCommandName.QUERY)
      .addBlazeFlags(BlazeFlags.KEEP_GOING, BlazeFlags.NO_IMPLICIT_DEPS, BlazeFlags.NO_HOST_DEPS)
      .addBlazeFlags(query)
      .build();

    ExternalTask.builder(workspaceRoot, command)
      .context(context)
      .stdout(stdout)
      .stderr(LineProcessingOutputStream.of(new IssueOutputLineProcessor(project, context, workspaceRoot)))
      .build()
      .run();
  }
}
