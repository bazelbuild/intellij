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
package com.google.idea.blaze.base.sync;

import static com.google.common.truth.Truth.assertThat;

import com.google.idea.blaze.base.BlazeIntegrationTestCase;
import com.google.idea.blaze.base.command.BlazercMigrator;
import com.google.idea.blaze.base.command.BlazercMigrator.BlazercMigrationReason;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class BlazercMigratorTest extends BlazeIntegrationTestCase {
  private static final String USER_BLAZERC = ".blazerc";
  private static final String PLATFORM_PREFIX_KEY = "idea.platform.prefix";
  private final BlazeContext context = BlazeContext.create();

  @Before
  public void setup() {
    System.setProperty(PLATFORM_PREFIX_KEY, "AndroidStudio");
  }

  @Test
  public void testCopyBlazercToWorkspaceWhenAbsent() {
    MockBlazercMigrator blazercMigrator = getMockBlazercMigrator(true);
    assertThat(blazercMigrator.needMigration())
        .isEqualTo(BlazercMigrationReason.WORKSPACE_BLAZERC_ABSENT);

    ApplicationManager.getApplication()
        .runWriteAction(() -> blazercMigrator.promptAndMigrate(context));

    VirtualFile workspaceBlazerc = blazercMigrator.getWorkspaceBlazercDir().findChild(USER_BLAZERC);
    assertThat(workspaceBlazerc).isNotNull();
    assertThat(workspaceBlazerc.exists()).isTrue();
    assertThat(blazercMigrator.needMigration())
        .isEqualTo(BlazercMigrationReason.NO_MIGRATION_NEEDED);
  }

  @Test
  public void testCopyBlazercToWorkspaceWhenOutOfSync() {
    MockBlazercMigrator blazercMigrator = getMockBlazercMigrator(true);
    workspace.createFile(new WorkspacePath(USER_BLAZERC), "workspace-blazerc");
    assertThat(blazercMigrator.needMigration())
        .isEqualTo(BlazercMigrationReason.WORKSPACE_BLAZERC_OUT_OF_SYNC);

    ApplicationManager.getApplication()
        .runWriteAction(() -> blazercMigrator.promptAndMigrate(context));

    VirtualFile workspaceBlazerc = blazercMigrator.getWorkspaceBlazercDir().findChild(USER_BLAZERC);
    assertThat(workspaceBlazerc).isNotNull();
    assertThat(workspaceBlazerc.exists()).isTrue();
    assertThat(blazercMigrator.needMigration())
        .isEqualTo(BlazercMigrationReason.NO_MIGRATION_NEEDED);
  }

  @Test
  public void testDoNotCopyBlazercToWorkspace() {
    MockBlazercMigrator blazercMigrator = getMockBlazercMigrator(false);
    assertThat(blazercMigrator.needMigration())
        .isEqualTo(BlazercMigrationReason.WORKSPACE_BLAZERC_ABSENT);

    ApplicationManager.getApplication()
        .runWriteAction(() -> blazercMigrator.promptAndMigrate(context));

    VirtualFile workspaceBlazerc = blazercMigrator.getWorkspaceBlazercDir().findChild(USER_BLAZERC);
    assertThat(workspaceBlazerc).isNull();
  }

  @Test
  public void testNoBlazercMigrationNeeded() {
    MockBlazercMigrator blazercMigrator = getMockBlazercMigrator(true);
    workspace.createFile(new WorkspacePath(USER_BLAZERC), "home-blazerc");
    assertThat(blazercMigrator.needMigration())
        .isEqualTo(BlazercMigrationReason.NO_MIGRATION_NEEDED);
  }

  @Test
  public void testBlazercMigrationOnNonAndroidStudio() {
    System.setProperty(PLATFORM_PREFIX_KEY, "Idea");
    MockBlazercMigrator blazercMigrator = getMockBlazercMigrator(true);
    assertThat(blazercMigrator.needMigration())
        .isEqualTo(BlazercMigrationReason.NO_MIGRATION_NEEDED);
  }

  private MockBlazercMigrator getMockBlazercMigrator(boolean userResponseToYesNoDialog) {
    VirtualFile homeBlazerc = fileSystem.createFile(USER_BLAZERC, "home-blazerc");
    assertThat(homeBlazerc).isNotNull();
    assertThat(homeBlazerc.exists()).isTrue();

    VirtualFile workspaceDir = fileSystem.findFile("workspace");
    assertThat(workspaceDir).isNotNull();
    assertThat(workspaceDir.exists()).isTrue();

    return new MockBlazercMigrator(homeBlazerc, workspaceDir, userResponseToYesNoDialog);
  }

  static class MockBlazercMigrator extends BlazercMigrator {
    private final boolean userResponseToYesNoDialog;
    private final VirtualFile workspaceBlazercDir;

    public MockBlazercMigrator(
        VirtualFile homeBlazerc,
        VirtualFile workspaceBlazercDir,
        boolean userResponseToYesNoDialog) {
      super(homeBlazerc, workspaceBlazercDir);
      this.userResponseToYesNoDialog = userResponseToYesNoDialog;
      this.workspaceBlazercDir = workspaceBlazercDir;
    }

    public VirtualFile getWorkspaceBlazercDir() {
      return workspaceBlazercDir;
    }

    @Override
    protected int showYesNoDialog() {
      return userResponseToYesNoDialog ? Messages.YES : Messages.NO;
    }
  }
}
