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

import com.google.common.collect.Lists;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.projectview.ProjectView;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.projectview.parser.ParseContext;
import com.google.idea.blaze.base.projectview.parser.ProjectViewParser;
import com.google.idea.blaze.base.projectview.section.ListSection;
import com.google.idea.blaze.base.projectview.section.ListSectionParser;
import com.google.idea.blaze.base.projectview.section.ProjectViewDefaultValueProvider;
import com.google.idea.blaze.base.projectview.section.SectionKey;
import com.google.idea.blaze.base.projectview.section.SectionParser;
import com.google.idea.blaze.base.settings.Blaze.BuildSystem;
import com.google.idea.blaze.base.ui.BlazeValidationError;
import com.intellij.util.PathUtil;
import java.util.List;
import javax.annotation.Nullable;
import org.jetbrains.annotations.NotNull;

/** "directories" section. */
public class DirectorySection {
  public static final SectionKey<DirectoryEntry, ListSection<DirectoryEntry>> KEY =
      SectionKey.of("directories");
  public static final SectionParser PARSER = new DirectorySectionParser();

  private static class DirectorySectionParser extends ListSectionParser<DirectoryEntry> {
    public DirectorySectionParser() {
      super(KEY);
    }

    @Nullable
    @Override
    protected DirectoryEntry parseItem(ProjectViewParser parser, ParseContext parseContext) {
      String text = parseContext.current().text;
      boolean excluded = text.startsWith("-");
      text = excluded ? text.substring(1) : text;

      text = PathUtil.getCanonicalPath(text);

      List<BlazeValidationError> errors = Lists.newArrayList();
      if (!WorkspacePath.validate(text, errors)) {
        parseContext.addErrors(errors);
        return null;
      }
      return new DirectoryEntry(new WorkspacePath(text), !excluded);
    }

    @Override
    protected void printItem(@NotNull DirectoryEntry item, @NotNull StringBuilder sb) {
      sb.append(item.toString());
    }

    @Override
    public ItemType getItemType() {
      return ItemType.FileSystemItem;
    }

    @Override
    public String quickDocs() {
      return "A list of project directories that will be added as source.";
    }
  }

  static class DirectoriesProjectViewDefaultValueProvider
      implements ProjectViewDefaultValueProvider {
    @Override
    public ProjectView addProjectViewDefaultValue(
        BuildSystem buildSystem, ProjectViewSet projectViewSet, ProjectView topLevelProjectView) {
      if (!topLevelProjectView.getSectionsOfType(KEY).isEmpty()) {
        return topLevelProjectView;
      }
      ListSection.Builder<DirectoryEntry> builder = ListSection.builder(KEY);
      builder.add(TextBlock.of("  # Add the directories you want added as source here"));
      if (buildSystem == BuildSystem.Bazel) {
        builder.add(TextBlock.of("  # By default, we've added your entire workspace ('.')"));
        builder.add(DirectoryEntry.include(new WorkspacePath(".")));
      }
      builder.add(TextBlock.newLine());
      return ProjectView.builder(topLevelProjectView).add(builder).build();
    }

    @Override
    public SectionKey<?, ?> getSectionKey() {
      return KEY;
    }
  }
}
