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
package com.google.idea.blaze.base.command;

import static com.google.common.base.StandardSystemProperty.USER_HOME;

import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.output.SummaryOutput;
import com.google.idea.blaze.base.scope.output.SummaryOutput.Prefix;
import com.google.idea.common.experiments.BoolExperiment;
import com.google.idea.common.util.MorePlatformUtils;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import javax.annotation.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

/** Utilities to support migrating user .blazerc from home directory to workspace root */
public class BlazercMigrator {
  private static final String USER_BLAZERC = ".blazerc";
  private static final BoolExperiment ENABLED =
      new BoolExperiment("blaze.sync.runner.enablebuildapi", true);
  private static final Logger logger = Logger.getInstance(BlazercMigrator.class);
  private final VirtualFile homeBlazerc;
  private final VirtualFile workspaceBlazercDir;
  private BlazercMigrationReason blazercMigrationReason;

  /** Reason for .blazerc migration */
  public enum BlazercMigrationReason {
    NO_MIGRATION_NEEDED {
      @Override
      @Nullable
      String getDisplayText(String homeBlazercPath, String workspaceBlazercDirPath) {
        return null;
      }
    },
    WORKSPACE_BLAZERC_ABSENT {
      @Override
      String getDisplayText(String homeBlazercPath, String workspaceBlazercDirPath) {
        return String.format(
            "You have a .blazerc file present in your home folder. The Blaze invocations from"
                + " the IDE won't be able to read the settings from this file. If you want this"
                + " blazerc to be used, then we recommend copying it into your workspace. Do"
                + " you want the IDE to copy the blazerc from \n"
                + "%s\n"
                + " to \n"
                + "%s?",
            homeBlazercPath, workspaceBlazercDirPath);
      }
    },
    WORKSPACE_BLAZERC_OUT_OF_SYNC {
      @Override
      String getDisplayText(String homeBlazercPath, String workspaceBlazercDirPath) {
        return String.format(
            "The contents of the .blazerc files present in your home folder and your workspace"
                + " do not match. The Blaze invocations from the IDE will use the .blazerc file"
                + " from your workspace. Do you want the IDE to copy the blazerc from \n"
                + "%s\n"
                + " to \n"
                + "%s?",
            homeBlazercPath, workspaceBlazercDirPath);
      }
    };

    abstract String getDisplayText(String homeBlazercPath, String workspaceBlazercDirPath);
  }

  public BlazercMigrator(Project project) {
    this.homeBlazerc = VfsUtil.findFileByIoFile(new File(USER_HOME.value(), USER_BLAZERC), true);
    this.workspaceBlazercDir =
        VfsUtil.findFileByIoFile(WorkspaceRoot.fromProject(project).directory(), true);
  }

  @VisibleForTesting
  public BlazercMigrator(VirtualFile homeBlazerc, VirtualFile workspaceBlazercDir) {
    this.homeBlazerc = homeBlazerc;
    this.workspaceBlazercDir = workspaceBlazercDir;
  }

  public void promptAndMigrate(BlazeContext context) {
    if (!promptMigration()) {
      return;
    }
    try {
      VirtualFile workspaceBlazerc = VfsUtil.findRelativeFile(workspaceBlazercDir, USER_BLAZERC);
      if (workspaceBlazerc != null && workspaceBlazerc.exists()) {
        workspaceBlazerc.delete(this);
      }
      VfsUtil.copy(this, homeBlazerc, workspaceBlazercDir);
      context.output(
          SummaryOutput.output(Prefix.INFO, "Copied .blazerc from home to workspace root")
              .log()
              .dedupe());
    } catch (IOException e) {
      context.output(
          SummaryOutput.output(
                  Prefix.INFO,
                  "Error copying .blazerc from home to workspace root; syncing without one.")
              .log()
              .dedupe());
      logger.error(e.getMessage());
    }
  }

  public BlazercMigrationReason needMigration() {
    VirtualFile workspaceBlazerc = workspaceBlazercDir.findChild(USER_BLAZERC);
    if (!ENABLED.getValue()
        || homeBlazerc == null
        || !homeBlazerc.exists()
        || !MorePlatformUtils.isAndroidStudio()) {
      blazercMigrationReason = BlazercMigrationReason.NO_MIGRATION_NEEDED;
    } else if (workspaceBlazerc == null || !workspaceBlazerc.exists()) {
      blazercMigrationReason = BlazercMigrationReason.WORKSPACE_BLAZERC_ABSENT;
    } else {
      try {
        blazercMigrationReason =
            Arrays.equals(workspaceBlazerc.contentsToByteArray(), homeBlazerc.contentsToByteArray())
                ? BlazercMigrationReason.NO_MIGRATION_NEEDED
                : BlazercMigrationReason.WORKSPACE_BLAZERC_OUT_OF_SYNC;
      } catch (IOException e) {
        logger.error(
            String.format(
                "Error reading the .blazerc files; migration not triggered. \n %s",
                e.getMessage()));
        blazercMigrationReason = BlazercMigrationReason.NO_MIGRATION_NEEDED;
      }
    }
    return blazercMigrationReason;
  }

  private boolean promptMigration() {
    int response = showYesNoDialog();
    logger.info("User opted for .blazerc migration: " + (response == Messages.YES ? "YES" : "NO"));
    return response == Messages.YES;
  }

  @VisibleForTesting
  protected int showYesNoDialog() {
    return Messages.showYesNoDialog(
        blazercMigrationReason.getDisplayText(USER_HOME.value(), workspaceBlazercDir.getPath()),
        "Blaze Configuration",
        null);
  }
}
