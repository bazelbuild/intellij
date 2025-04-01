/*
 * Copyright 2022 The Bazel Authors. All rights reserved.
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
import com.intellij.openapi.project.Project;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Runs the blaze run command, on gazelle.
 * The results may be cached in the workspace.
 */
public abstract class GazelleRunner {

    public static GazelleRunner getInstance() {
        return ApplicationManager.getApplication().getService(
                GazelleRunner.class);
    }

    protected static BlazeCommand createGazelleRunCommand(
            BuildInvoker invoker,
            List<String> blazeFlags,
            Label gazelleTarget,
            Collection<WorkspacePath> directories,
            Project project
    ) {
        BlazeCommand.Builder builder = BlazeCommand.builder(invoker,
                BlazeCommandName.RUN, project);
        builder.addBlazeFlags(blazeFlags);
        builder.addTargets(gazelleTarget);
        List<String> directoriesToRegenerate =
                directories.stream().map(WorkspacePath::toString).collect(
                        Collectors.toList());
        builder.addExeFlags(directoriesToRegenerate);
        return builder.build();
    }

    /**
     * Run the provided Gazelle target via Blaze.
     *
     * @return GazelleRunResult,
     * an enum determining whether the command succeeded, failed to run, or
     * ran with errors.
     */
    public abstract GazelleRunResult runBlazeGazelle(
            BlazeContext context,
            BuildInvoker invoker,
            WorkspaceRoot workspaceRoot,
            List<String> blazeFlags,
            Label gazelleTarget,
            Collection<WorkspacePath> directories,
            ImmutableList<Parser> issueParsers,
            Project project);
}
