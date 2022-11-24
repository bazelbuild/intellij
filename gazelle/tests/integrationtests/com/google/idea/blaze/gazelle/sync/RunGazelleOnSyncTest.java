/*
 * Copyright 2018 The Bazel Authors. All rights reserved.
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

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.command.BlazeCommand;
import com.google.idea.blaze.base.command.BlazeCommandName;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.settings.BlazeUserSettings;
import com.google.idea.blaze.base.sync.BlazeSyncIntegrationTestCase;
import com.google.idea.blaze.base.sync.BlazeSyncParams;
import com.google.idea.blaze.base.sync.SyncMode;
import com.google.idea.blaze.gazelle.GazelleRunner;
import com.google.idea.blaze.gazelle.GazelleUserSettings;
import com.intellij.openapi.application.ApplicationManager;
import java.io.File;
import java.util.Optional;
import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class RunGazelleOnSyncTest extends BlazeSyncIntegrationTestCase {

  @Override
  protected boolean isLightTestCase() {
    return false;
  }

  private void runBazelSync() {
    GazelleUserSettings.getInstance().setGazelleHeadless(true);

    BlazeSyncParams syncParams =
        BlazeSyncParams.builder()
            .setTitle("Full Sync")
            .setSyncMode(SyncMode.INCREMENTAL)
            .setSyncOrigin("test")
            .setAddProjectViewTargets(true)
            .build();
    runBlazeSync(syncParams);
  }

  private MockBlazeGazelleRunnerImpl getMockRunner() {
    return (MockBlazeGazelleRunnerImpl) GazelleRunner.getInstance();
  }

  @After
  public void clearState() {
    // Some test modify global state, which doesn't get reset by regular cleanup.
    GazelleUserSettings.getInstance().clearGazelleTarget();
    getMockRunner().clean();
  }

  private String GAZELLE_RUN_COMMAND = "bazel run --tool_tag=.* --curses=no --color=yes --progress_in_terminal_title=no -- //:gazelle";

  // For now, we can't run bazel on tests, since some of these tests might be run remotely.
  // Therefore, we just check that the Bazel command that would have been run has the right shape.
  private void assertRunnerCommandIs(Optional<String> wantCommand) {
    MockBlazeGazelleRunnerImpl runner = getMockRunner();
    Optional<BlazeCommand> gotCommand = runner.command;

    assertThat(wantCommand.isEmpty()).isEqualTo(gotCommand.isEmpty());
    if (wantCommand.isEmpty() && gotCommand.isEmpty())  {
      return;
    }
    String gotAsString = gotCommand.get().toString().strip();
    assertThat(gotAsString).matches(wantCommand.get());
  }

  @Test
  public void testGazelleDoesntRunWhenNotConfigured() {
    setProjectView(
        "directories:",
        "  ."
    );

    runBazelSync();

    errorCollector.assertNoIssues();

    assertRunnerCommandIs(Optional.empty());
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

    assertRunnerCommandIs(Optional.of(GAZELLE_RUN_COMMAND));
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

    assertRunnerCommandIs(Optional.of(GAZELLE_RUN_COMMAND));
  }
}
