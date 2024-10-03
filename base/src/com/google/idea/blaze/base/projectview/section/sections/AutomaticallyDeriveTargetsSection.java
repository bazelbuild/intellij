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
import com.google.idea.blaze.base.projectview.section.ProjectViewDefaultValueProvider;
import com.google.idea.blaze.base.projectview.section.ScalarSection;
import com.google.idea.blaze.base.projectview.section.SectionKey;
import com.google.idea.blaze.base.projectview.section.SectionParser;
import com.google.idea.blaze.base.settings.BuildSystemName;

import static com.google.idea.blaze.base.projectview.parser.ProjectViewParser.TEMPORARY_LINE_NUMBER;

/** If set to true, automatically derives targets from the project directories. */
public class AutomaticallyDeriveTargetsSection {
  public static final SectionKey<Boolean, ScalarSection<Boolean>> KEY =
      SectionKey.of("derive_targets_from_directories");
  public static final SectionParser PARSER = new BooleanSectionParser(
          KEY,
          "If set to true, project targets will be derived from the directories."
  );

  static class DefaultValueProvider implements ProjectViewDefaultValueProvider {
    @Override
    public ProjectView addProjectViewDefaultValue(
        BuildSystemName buildSystemName,
        ProjectViewSet projectViewSet,
        ProjectView topLevelProjectView) {
      if (!topLevelProjectView.getSectionsOfType(KEY).isEmpty()) {
        return topLevelProjectView;
      }
      return ProjectView.builder(topLevelProjectView)
          .add(
              TextBlockSection.of(
                  TextBlock.of(
                          TEMPORARY_LINE_NUMBER,
                      "# Automatically includes all relevant targets under the 'directories'"
                          + " above")))
          .add(ScalarSection.builder(KEY).set(true))
          .add(TextBlockSection.of(TextBlock.newLine(TEMPORARY_LINE_NUMBER)))
          .build();
    }

    @Override
    public SectionKey<?, ?> getSectionKey() {
      return KEY;
    }
  }
}
