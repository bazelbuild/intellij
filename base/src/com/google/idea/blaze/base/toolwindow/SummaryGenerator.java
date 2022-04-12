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
package com.google.idea.blaze.base.toolwindow;

import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.output.PrintOutput;
import com.google.idea.blaze.base.scope.output.PrintOutput.OutputType;
import com.google.idea.blaze.base.toolwindow.SyncTask.SubType;
import com.intellij.openapi.util.text.StringUtil;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

/** Generates summary for Sync tasks to send to propagate to the consoles of the nodes */
public class SummaryGenerator {

  private SummaryGenerator() {}

  public static void printShardCountSummary(BlazeContext context, int targetCount, int shardCount) {
    context
        .getRootContext()
        .output(
            new PrintOutput(
                String.format(
                    "INFO: %d "
                        + StringUtil.pluralize("target", targetCount)
                        + " found, building on Rabbit using %d "
                        + StringUtil.pluralize("shard", shardCount),
                    targetCount,
                    shardCount)));
  }

  public static PrintOutput getTaskStartedSummary(Task task) {
    if (!(task instanceof SyncTask)) {
      return null;
    }
    SyncTask syncTask = (SyncTask) task;
    if (syncTask.getSubType().equals(SubType.BUILD_SHARD)) {
      return null;
    }
    String stringToPrint =
        truncateTime(task.getStartTime().get()) + "\t" + syncTask.getName() + " started";
    return new PrintOutput(stringToPrint);
  }

  public static PrintOutput getTaskFinishedSummary(Task task) {
    if (!(task instanceof SyncTask)) {
      return null;
    }
    SyncTask syncTask = (SyncTask) task;
    if (syncTask.getSubType().equals(SubType.BUILD_SHARD) && !syncTask.getHasErrors()) {
      return null;
    }
    StringBuilder stringToPrint = new StringBuilder();
    OutputType outputType = syncTask.getHasErrors() ? OutputType.ERROR : OutputType.NORMAL;
    String finished = syncTask.getHasErrors() ? " finished with errors" : " finished";
    String taskName =
        syncTask.getSubType().equals(SubType.BUILD_SHARD)
            ? syncTask.getName() + " " + syncTask.getInvocationId().substring(0, 8) + "..."
            : syncTask.getName();
    stringToPrint
        .append(truncateTime(task.getEndTime().get()))
        .append("\t")
        .append(taskName)
        .append(finished);
    if (syncTask.getSubType().equals(SubType.BUILD_SHARD)) {
      stringToPrint
          .append("; see build results at http://sponge2/")
          .append(syncTask.getInvocationId());
    }
    return new PrintOutput(stringToPrint.toString(), outputType);
  }

  public static boolean extractInvocationID(Task task, PrintOutput output) {
    if (!(task instanceof SyncTask)) {
      return false;
    }
    SyncTask syncTask = (SyncTask) task;
    String outputText = output.getText().toLowerCase(Locale.ROOT);
    if (outputText.contains("invocation id:")) {
      syncTask.setInvocationId(outputText.substring(outputText.lastIndexOf(" ")).trim());
      syncTask.setState(syncTask.getInvocationId().substring(0, 8) + "...");
      return true;
    }
    return false;
  }

  private static String truncateTime(Instant instant) {
    return instant.atZone(ZoneOffset.UTC).toLocalTime().truncatedTo(ChronoUnit.SECONDS).toString();
  }
}
