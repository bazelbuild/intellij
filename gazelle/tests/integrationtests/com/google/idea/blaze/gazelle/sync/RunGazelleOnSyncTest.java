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
package com.google.idea.blaze.gazelle.sync;

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.bazel.BuildSystem;
import com.google.idea.blaze.base.command.BlazeCommand;
import com.google.idea.blaze.base.issueparser.BlazeIssueParser;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.sync.BlazeSyncIntegrationTestCase;
import com.google.idea.blaze.base.sync.BlazeSyncParams;
import com.google.idea.blaze.base.sync.SyncMode;
import com.google.idea.blaze.gazelle.GazelleRunResult;
import com.google.idea.blaze.gazelle.GazelleRunner;
import com.google.idea.blaze.gazelle.GazelleUserSettings;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.Collection;
import java.util.List;

import static com.google.common.truth.Truth.assertThat;

@RunWith(JUnit4.class)
public class RunGazelleOnSyncTest extends BlazeSyncIntegrationTestCase {
    private final String GAZELLE_RUN_COMMAND =
            "bazel run --tool_tag=.* --curses=no --color=yes --progress_in_terminal_title=no -- //:gazelle";

    private MockGazelleRunner mockGazelleRunner;

    @Override
    protected boolean isLightTestCase() {
        return false;
    }

    private void runBazelSync() {
        GazelleUserSettings.getInstance().setGazelleHeadless(true);

        BlazeSyncParams syncParams = BlazeSyncParams.builder().setTitle(
                "Full Sync").setSyncMode(SyncMode.INCREMENTAL).setSyncOrigin(
                "test").setAddProjectViewTargets(true).build();

        runBlazeSync(syncParams);
    }

    @Before
    public void setUpTest() {
        mockGazelleRunner = new MockGazelleRunner();
        registerApplicationService(GazelleRunner.class, mockGazelleRunner);
    }

    @After
    public void clearState() {
        // Some tests modify global state, which doesn't get reset by
        // regular cleanup.
        GazelleUserSettings.getInstance().clearGazelleTarget();
    }

    @Test
    public void testGazelleDoesntRunWhenNotConfigured() {
        setProjectView(
            "directories:",
            "  ."
        );

        runBazelSync();

        errorCollector.assertNoIssues();
        assertThat(mockGazelleRunner.command).isNull();
    }

    @Test
    public void testRunGazelleWhenConfiguredInProject() {
        setProjectView(
            "directories:",
            "  .",
            "gazelle_target: //:gazelle"
        );

        runBazelSync();

        errorCollector.assertNoIssues();
        assertThat(mockGazelleRunner.command).isNotNull();
        assertThat(mockGazelleRunner.command.toString().strip()).matches(
                GAZELLE_RUN_COMMAND);
    }

    @Test
    public void testRunGazelleWhenConfiguredGlobally() {
        GazelleUserSettings gazelleSettings = GazelleUserSettings.getInstance();
        gazelleSettings.setGazelleTarget("//:gazelle");
        setProjectView(
            "directories:",
            "  ."
        );

        runBazelSync();

        errorCollector.assertNoIssues();
        assertThat(mockGazelleRunner.command).isNotNull();
        assertThat(mockGazelleRunner.command.toString().strip()).matches(
                GAZELLE_RUN_COMMAND);
    }

    @Test
    public void testGazelleDoesntRunWithInvalidGazelleTarget() {
        GazelleUserSettings gazelleSettings = GazelleUserSettings.getInstance();
        gazelleSettings.setGazelleTarget("INVALID_TARGET");
        setProjectView(
                "directories:",
                "  ."
        );

        runBazelSync();

        errorCollector.assertNoIssues();
        assertThat(mockGazelleRunner.command).isNull();
    }

    private static class MockGazelleRunner extends GazelleRunner {
        BlazeCommand command;

        @Override
        public GazelleRunResult runBlazeGazelle(BlazeContext context,
                                                BuildSystem.BuildInvoker invoker,
                                                WorkspaceRoot workspaceRoot,
                                                List<String> blazeFlags,
                                                Label gazelleTarget,
                                                Collection<WorkspacePath> directories,
                                                ImmutableList<BlazeIssueParser.Parser> issueParsers) {
            // For now, we can't run bazel on tests, since some of these tests
            // might be run remotely. Therefore, we just check that the Bazel
            // command that would have been run has the right shape.
            command = GazelleRunner.createGazelleRunCommand(invoker, blazeFlags,
                    gazelleTarget, directories);
            return GazelleRunResult.SUCCESS;
        }
    }
}
