/*
 * Copyright 2019 The Bazel Authors. All rights reserved.
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

import com.google.idea.blaze.base.projectview.ProjectView;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.projectview.parser.ParseContext;
import com.google.idea.blaze.base.projectview.parser.ProjectViewParser;
import com.google.idea.blaze.base.projectview.section.ProjectViewDefaultValueProvider;
import com.google.idea.blaze.base.projectview.section.ScalarSection;
import com.google.idea.blaze.base.projectview.section.ScalarSectionParser;
import com.google.idea.blaze.base.projectview.section.SectionKey;
import com.google.idea.blaze.base.projectview.section.SectionParser;
import com.google.idea.blaze.base.settings.BuildSystemName;
import javax.annotation.Nullable;

/** If set to true, automatically derives targets from the project directories. */
public class SyncManualTargetsSection {
  public static final SectionKey<Boolean, ScalarSection<Boolean>> KEY =
      SectionKey.of("allow_manual_targets_sync");
  public static final SectionParser PARSER = new SyncManualTargetsParser();

  private static class SyncManualTargetsParser
      extends ScalarSectionParser<Boolean> {
    SyncManualTargetsParser() {
      super(KEY, ':');
    }

    @Override
    @Nullable
    protected Boolean parseItem(ProjectViewParser parser, ParseContext parseContext, String text) {
      if (text.equals("true")) {
        return true;
      }
      if (text.equals("false")) {
        return false;
      }
      parseContext.addError(
          "'allow_manual_targets_sync' must be set to 'true' or 'false' (e.g."
              + " 'allow_manual_targets_sync: true')");
      return null;
    }

    @Override
    protected void printItem(StringBuilder sb, Boolean item) {
      sb.append(item);
    }

    @Override
    public ItemType getItemType() {
      return ItemType.Other;
    }

    @Override
    public String quickDocs() {
      return "If set to true, project targets will be derived from the directories.";
    }
  }
}
