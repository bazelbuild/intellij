/*
 * Copyright 2023 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.projectview.section.sections;

import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.projectview.parser.ParseContext;
import com.google.idea.blaze.base.projectview.parser.ProjectViewParser;
import com.google.idea.blaze.base.projectview.section.ScalarSection;
import com.google.idea.blaze.base.projectview.section.ScalarSectionParser;
import com.google.idea.blaze.base.projectview.section.SectionKey;
import com.google.idea.blaze.base.projectview.section.SectionParser;
import java.io.File;
import javax.annotation.Nullable;

/** A section to specify sync performance tracking script */
public final class SyncTelemetryScriptSection {
  public static final SectionKey<WorkspacePath, ScalarSection<WorkspacePath>> KEY =
      SectionKey.of("sync_telemetry_script");
  public static final SectionParser PARSER = new SyncTelemetryScriptSectionParser();

  private static class SyncTelemetryScriptSectionParser extends ScalarSectionParser<WorkspacePath> {
    public SyncTelemetryScriptSectionParser() {
      super(KEY, ':');
    }

    @Nullable
    @Override
    protected WorkspacePath parseItem(
        ProjectViewParser parser, ParseContext parseContext, String text) {
      String error = WorkspacePath.validate(text);
      if (error != null) {
        parseContext.addError(error);
        return null;
      }

      WorkspacePath workspacePath = new WorkspacePath(text);
      if (parser.isRecursive()) {
        File trackingScript = parseContext.getWorkspacePathResolver().resolveToFile(workspacePath);
        if (trackingScript == null || !trackingScript.exists()) {
          parseContext.addError(
              String.format(
                  "Could not resolve telemetry script: %s",
                  workspacePath.relativePath()));
        } else if (!trackingScript.canExecute()) {
          parseContext.addError(
              String.format(
                  "Telemetry script is not executable: %s",
                  workspacePath.relativePath()));
        }
      }
      return workspacePath;
    }

    @Override
    protected void printItem(StringBuilder sb, WorkspacePath section) {
      sb.append(section);
    }

    @Override
    public ItemType getItemType() {
      return ItemType.FileItem;
    }

    @Override
    public String quickDocs() {
      return "Specifies executable script to track plugin sync performance";
    }
  }

  private SyncTelemetryScriptSection() {}
}
