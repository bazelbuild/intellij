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
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.output.IssueOutput;
import com.google.idea.blaze.base.sync.workspace.WorkspaceHelper;
import com.intellij.openapi.project.Project;

import javax.annotation.Nullable;
import java.io.File;
import java.io.Serializable;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

final class ProtoSettingsSyncSections {
  static class WorkspaceDir implements Serializable {
    @Nullable final String workspace;
    final String directoryPath;

    /**
     * @param workspace the workspace that this directory is in.
     * @param directoryPath the path to the directory within the workspace.
     */
    WorkspaceDir(@Nullable String workspace, String directoryPath) {
      this.workspace = workspace;
      this.directoryPath = directoryPath;
    }

    /**
     * @return File system path to the specified directory. This will either be a local workspace
     *     path or an external workspace path
     */
    @Nullable
    String resolve(BlazeContext context, Project project) {
      // start with the local workspace root.
      WorkspaceRoot workspaceRoot = WorkspaceRoot.fromProject(project);
      if (workspace != null
          // update to the external workspace if needed.
          && (workspaceRoot = WorkspaceHelper.resolveExternalWorkspace(project, workspace))
              == null) {
        IssueOutput.issue(
                IssueOutput.Category.NOTE, "external workspace " + workspace + " not found")
            .submit(context);
        return null;
      }

      Path protoDirectory =
          directoryPath == null
              ? workspaceRoot.directory().toPath()
              : workspaceRoot.directory().toPath().resolve(directoryPath);

      if (!protoDirectory.toFile().exists()) {
        IssueOutput.issue(
                IssueOutput.Category.NOTE, "proto directory " + toString() + " does not exist")
            .submit(context);
        return null;
      }
      return protoDirectory.toString();
    }

    @Override
    public String toString() {
      StringBuilder sb = new StringBuilder();
      if (workspace != null) {
        sb.append("@");
        sb.append(workspace);
      }
      if (directoryPath != null) {
        sb.append(
            Arrays.stream(directoryPath.split(File.separator))
                .collect(Collectors.joining("/", "//", "")));
      }
      return sb.toString();
    }
  }

  private static final String WS_ROOT_SEP = "//";
  private static final Splitter LABEL_PATH_SPLITTER = Splitter.on('/').trimResults(),
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
          return "directories that will be added to the proto plugin as roots.";
        }

        @Nullable
        @Override
        protected WorkspaceDir parseItem(ProjectViewParser parser, ParseContext parseContext) {
          String trimmed = parseContext.currentRawLine().trim();
          List<String> parts =
              ImmutableList.copyOf(
                  WS_SPLITTER.split(
                      trimmed.endsWith("/")
                          ? trimmed.substring(0, trimmed.length() - 1)
                          : trimmed));

          String workspace = null;
          String dirString;

          switch (parts.size()) {
            case 0:
              parseContext.addError(trimmed + " contained no elements");
              return null;
            case 1:
              dirString = parts.get(0);
              break;
            case 2:
              dirString = parts.get(1);
              if (!parts.get(0).isEmpty()) {
                if (!parts.get(0).startsWith("@")) {
                  parseContext.addError(trimmed + " namespace must start with @");
                  return null;
                } else if ((workspace = parts.get(0).substring(1)).isEmpty()) {
                  parseContext.addError(trimmed + " contains an empty namespace");
                  return null;
                }
              }
              break;
            default:
              parseContext.addError(
                  trimmed
                      + " invalid: "
                      + WS_ROOT_SEP
                      + " should be used only once at the start of a path or after the workspace");
              return null;
          }

          if (dirString != null) {
            parts = ImmutableList.copyOf(LABEL_PATH_SPLITTER.split(dirString));
            if (parts.contains("")) {
              parseContext.addError(trimmed + " contained an empty path element");
              return null;
            }
          }
          return new WorkspaceDir(workspace, SYS_PATH_JOINER.join(parts));
        }

        @Override
        protected void printItem(WorkspaceDir item, StringBuilder sb) {
          sb.append(item);
        }
      };

  static ImmutableList<SectionParser> PARSERS = ImmutableList.of(PROTO_IMPORT_ROOTS);

  static boolean hasProtoEntries(ProjectViewSet projectViewSet) {
    return getProtoImportRoots(projectViewSet).findFirst().isPresent();
  }

  private static Stream<WorkspaceDir> getProtoImportRoots(ProjectViewSet projectViewSet) {
    return projectViewSet
        .getSections(ProtoSettingsSyncSections.PROTO_IMPORT_ROOTS.getSectionKey())
        .stream()
        .flatMap(i -> i.items().stream());
  }

  static Stream<String> getProtoImportRoots(
      BlazeContext context, Project project, ProjectViewSet projectViewSet) {
    return getProtoImportRoots(projectViewSet)
        .map(wd -> wd.resolve(context, project))
        .filter(Objects::nonNull);
  }
}
