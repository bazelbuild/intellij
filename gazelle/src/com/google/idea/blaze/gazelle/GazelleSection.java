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

import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.projectview.parser.ParseContext;
import com.google.idea.blaze.base.projectview.parser.ProjectViewParser;
import com.google.idea.blaze.base.projectview.section.ScalarSection;
import com.google.idea.blaze.base.projectview.section.ScalarSectionParser;
import com.google.idea.blaze.base.projectview.section.SectionKey;
import com.google.idea.blaze.base.projectview.section.SectionParser;
import org.jetbrains.annotations.Nullable;

/** Section for project-specific gazelle configuration. */
public class GazelleSection {

  public static final SectionKey<Label, ScalarSection<Label>> KEY = SectionKey.of("gazelle_target");
  public static final SectionParser PARSER = new GazelleSectionParser();

  private static class GazelleSectionParser extends ScalarSectionParser<Label> {

    public GazelleSectionParser() {
      super(KEY, ':');
    }

    @Nullable
    @Override
    protected Label parseItem(ProjectViewParser parser, ParseContext parseContext, String text) {
      if (text == null) {
        return null;
      }
      String error = Label.validate(text);
      if (error != null) {
        parseContext.addError(error);
        return null;
      }
      return Label.create(text);
    }

    @Override
    protected void printItem(StringBuilder sb, Label value) {
      sb.append(value.toString());
    }

    @Override
    public ItemType getItemType() {
      return ItemType.Label;
    }

    @Override
    public String quickDocs() {
      return "Gazelle target used to refresh the project. If not specified, gazelle will not run on"
          + " sync.";
    }
  }
}
