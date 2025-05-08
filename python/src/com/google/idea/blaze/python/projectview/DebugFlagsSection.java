/*
 * Copyright 2025 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.python.projectview;

import com.google.idea.blaze.base.projectview.parser.ParseContext;
import com.google.idea.blaze.base.projectview.parser.ProjectViewParser;
import com.google.idea.blaze.base.projectview.section.ListSection;
import com.google.idea.blaze.base.projectview.section.ListSectionParser;
import com.google.idea.blaze.base.projectview.section.SectionKey;
import com.google.idea.blaze.base.projectview.section.SectionParser;
import com.google.idea.blaze.base.settings.Blaze;
import javax.annotation.Nullable;

/**
 * <p>These settings will allow the user to specify flags that should be applied in all cases
 * where a Python debug session is undertaken.</p>
 */

public class DebugFlagsSection {
  public static final SectionKey<String, ListSection<String>> KEY = SectionKey.of("python_debug_flags");
  public static final SectionParser PARSER = new DebugFlagsSection.DebugFlagsSectionParser();

  static class DebugFlagsSectionParser extends ListSectionParser<String> {
    DebugFlagsSectionParser() {
      super(KEY);
    }

    @Nullable
    @Override
    protected String parseItem(ProjectViewParser parser, ParseContext parseContext) {
      return parseContext.current().text;
    }

    @Override
    protected void printItem(String item, StringBuilder sb) {
      sb.append(item);
    }

    @Override
    public ItemType getItemType() {
      return ItemType.Other;
    }

    @Override
    public String quickDocs() {
      return String.format(
          "A set of flags that get passed to %s test/run during any python debug sessions.",
          Blaze.guessBuildSystemName());
    }
  }
}
