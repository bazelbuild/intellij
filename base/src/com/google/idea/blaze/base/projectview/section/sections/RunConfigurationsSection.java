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
package com.google.idea.blaze.base.projectview.section.sections;

import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.projectview.parser.ParseContext;
import com.google.idea.blaze.base.projectview.parser.ProjectViewParser;
import com.google.idea.blaze.base.projectview.section.ListSection;
import com.google.idea.blaze.base.projectview.section.ListSectionParser;
import com.google.idea.blaze.base.projectview.section.SectionKey;
import com.google.idea.blaze.base.projectview.section.SectionParser;
import javax.annotation.Nullable;

/** Allows users to import run configurations from XML files in their workspace. */
public class RunConfigurationsSection {
  public static final SectionKey<WorkspacePath, ListSection<WorkspacePath>> KEY =
      SectionKey.of("import_run_configurations");
  public static final SectionParser PARSER = new RunConfigurationsSectionParser();

  private static class RunConfigurationsSectionParser extends ListSectionParser<WorkspacePath> {
    private RunConfigurationsSectionParser() {
      super(KEY);
    }

    @Nullable
    @Override
    protected WorkspacePath parseItem(ProjectViewParser parser, ParseContext parseContext) {
      String text = parseContext.current().text;
      String error = WorkspacePath.validate(text);
      if (error != null) {
        parseContext.addError(error);
        return null;
      }
      return new WorkspacePath(text);
    }

    @Override
    protected void printItem(WorkspacePath item, StringBuilder sb) {
      sb.append(item);
    }

    @Override
    public ItemType getItemType() {
      return ItemType.FileSystemItem;
    }

    @Override
    public String quickDocs() {
      return "A list of XML files which will be imported as run configurations during sync.";
    }
  }
}
