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
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.projectview.parser.ParseContext;
import com.google.idea.blaze.base.projectview.parser.ProjectViewParser;
import com.google.idea.blaze.base.projectview.section.ListSectionParser;
import com.google.idea.blaze.base.projectview.section.SectionKey;
import com.google.idea.blaze.base.projectview.section.SectionParser;
import com.google.idea.blaze.base.scope.output.IssueOutput;
import com.google.idea.blaze.base.sync.workspace.WorkspaceHelper;
import com.intellij.openapi.project.Project;

import javax.annotation.Nullable;
import java.io.File;
import java.io.Serializable;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

final class ProtoSettingsSyncSections {
  static class WorkspaceDir implements Serializable {
    @Nullable final String workspace;
    final String directoryPath;

    WorkspaceDir(@Nullable String workspace, String directoryPath) {
      this.workspace = workspace;
      this.directoryPath = directoryPath;
    }

    @Nullable
    String resolve(WorkspaceRoot workspaceRoot, Project project) {
      if (workspace != null) {
        workspaceRoot = WorkspaceHelper.resolveExternalWorkspace(project, workspace);
        if (workspaceRoot == null) {
          IssueOutput.warn("external workspace " + workspace + " could not be found ");
          return null;
        }
      }
      if (directoryPath != null) {
        return workspaceRoot.directory().toPath().resolve(directoryPath).toString();
      } else {
        return workspaceRoot.directory().toPath().toString();
      }
    }
  }

  private static final String WS_ROOT_SEP = "//";
  private static final Splitter PATH_SPLITTER = Splitter.on('/').trimResults(),
      WS_SPLITTER = Splitter.on(WS_ROOT_SEP);
  private static final Joiner SYS_PATH_JOINER = Joiner.on(File.separator);

  static final ListSectionParser<WorkspaceDir> PROTO_IMPORT_ROOTS =
      new ListSectionParser<WorkspaceDir>(SectionKey.of("proto_import_roots")) {
        @Override
        public ItemType getItemType() {
          return ItemType.DirectoryItem;
        }

        @Override
        public String quickDocs() {
          return "directories that should be added as roots to the proto plugin.";
        }

        @Nullable
        @Override
        protected WorkspaceDir parseItem(ProjectViewParser parser, ParseContext parseContext) {

          String trimmed = parseContext.currentRawLine().trim();
          String canonical = trimmed;
          if (trimmed.endsWith("/")) {
            canonical = trimmed.substring(0, trimmed.length() - 1);
          }

          List<String> parts = WS_SPLITTER.splitToList(canonical);

          if (parts.size() < 1) {
            parseContext.addError(trimmed + " contained no elements");
            return null;
          }
          if (parts.size() > 2) {
            parseContext.addError(
                trimmed
                    + " invalid: "
                    + WS_ROOT_SEP
                    + " should be used only once at the start of a path or after the workspace");
            return null;
          }

          String workspace = null;
          String dirString;
          boolean startsWithAt = parts.get(0).startsWith("@");

          if (parts.size() == 1) {
            dirString = parts.get(0);
          } else if (parts.get(0).equals("") || startsWithAt) {
            if (startsWithAt) {
              workspace = parts.get(0);
              workspace = workspace.substring(1, workspace.length());
              if (workspace.isEmpty()) {
                parseContext.addError(trimmed + " contains an empty namespace");
                return null;
              }
            }
            dirString = parts.get(1);
          } else {
            parseContext.addError(trimmed + " namespace must start with @");
            return null;
          }

          String directoryPath = null;
          if (dirString != null) {
            parts = PATH_SPLITTER.splitToList(dirString);
            if (parts.contains("")) {
              parseContext.addError(trimmed + " contained an empty path element");
            }
            directoryPath = SYS_PATH_JOINER.join(parts);
          }
          return new WorkspaceDir(workspace, directoryPath);
        }

        @Override
        protected void printItem(WorkspaceDir item, StringBuilder sb) {
          if (item.workspace != null) {
            sb.append("@");
            sb.append(item.workspace);
          }
          if (item.directoryPath != null) {
            sb.append(
                Arrays.stream(item.directoryPath.split(File.separator))
                    .collect(Collectors.joining("/", "//", "")));
          }
        }
      };

  static ImmutableList<SectionParser> PARSERS = ImmutableList.of(PROTO_IMPORT_ROOTS);

  static boolean hasProtoEntries(ProjectViewSet projectViewSet) {
    return getImportRootsInternal(projectViewSet).findFirst().isPresent();
  }

  private static Stream<WorkspaceDir> getImportRootsInternal(ProjectViewSet projectViewSet) {
    return projectViewSet
        .getSections(ProtoSettingsSyncSections.PROTO_IMPORT_ROOTS.getSectionKey())
        .stream()
        .flatMap(i -> i.items().stream());
  }

  static Stream<String> getProtoImportRoots(Project project, ProjectViewSet projectViewSet) {
    WorkspaceRoot localProjectRoot = WorkspaceRoot.fromProject(project);
    return getImportRootsInternal(projectViewSet)
        .map(i -> i.resolve(localProjectRoot, project))
        .filter(Objects::nonNull);
  }
}
