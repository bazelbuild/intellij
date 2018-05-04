/*
 * Copyright 2018 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.contrib.protosync;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.projectview.parser.ParseContext;
import com.google.idea.blaze.base.projectview.parser.ProjectViewParser;
import com.google.idea.blaze.base.projectview.section.ListSectionParser;
import com.google.idea.blaze.base.projectview.section.SectionKey;
import com.google.idea.blaze.base.projectview.section.SectionParser;
import com.intellij.openapi.project.Project;

import java.io.File;
import java.nio.file.Path;
import java.util.stream.Stream;

final class ProtoSettingsSyncSections {
  private static final Joiner FILEPATH_JOINER = Joiner.on(File.separator);
  private static final Joiner PRINT_FORM_JOINER = Joiner.on("/");

  static final ListSectionParser<Path> PROTO_IMPORT_ROOTS =
      new ListSectionParser<Path>(SectionKey.of("proto_import_roots")) {
        @Override
        public ItemType getItemType() {
          return ItemType.DirectoryItem;
        }

        @Override
        public String quickDocs() {
          return "directories that should be added as roots to the proto plugin.";
        }

        @Override
        protected Path parseItem(ProjectViewParser parser, ParseContext parseContext) {
          String line = parseContext.currentRawLine().trim();
          if (FILEPATH_JOINER.equals(PRINT_FORM_JOINER)) {
            return new File(line).toPath();
          } else {
            return new File(FILEPATH_JOINER.join(line.split("/"))).toPath();
          }
        }

        @Override
        protected void printItem(Path item, StringBuilder sb) {
          if (FILEPATH_JOINER.equals(PRINT_FORM_JOINER)) {
            sb.append(item);
          } else {
            sb.append(PRINT_FORM_JOINER.join(item.toString().split(File.separator)));
          }
        }
      };

  static ImmutableList<SectionParser> PARSERS = ImmutableList.of(PROTO_IMPORT_ROOTS);

  static Stream<String> getProtoImportRoots(Project project, ProjectViewSet projectViewSet) {
    Path workspaceRoot = WorkspaceRoot.fromProject(project).directory().toPath();
    return projectViewSet
        .getSections(ProtoSettingsSyncSections.PROTO_IMPORT_ROOTS.getSectionKey())
        .stream()
        .flatMap(i -> i.items().stream())
        .map(i -> workspaceRoot.resolve(i).toString());
  }
}
