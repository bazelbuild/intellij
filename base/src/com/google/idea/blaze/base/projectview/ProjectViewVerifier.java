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
package com.google.idea.blaze.base.projectview;

import com.google.common.collect.Lists;
import com.google.idea.blaze.base.io.WorkspaceScanner;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.projectview.section.ListSection;
import com.google.idea.blaze.base.projectview.section.sections.DirectoryEntry;
import com.google.idea.blaze.base.projectview.section.sections.DirectorySection;
import com.google.idea.blaze.base.projectview.section.sections.ExcludedSourceSection;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.output.IssueOutput;
import com.google.idea.blaze.base.sync.BlazeSyncPlugin;
import com.google.idea.blaze.base.sync.projectview.WorkspaceLanguageSettings;
import com.intellij.openapi.util.io.FileUtil;
import java.util.List;

/** Verifies project views. */
public class ProjectViewVerifier {

  private static class MissingDirectoryIssueData extends IssueOutput.IssueData {
    public final WorkspacePath workspacePath;

    public MissingDirectoryIssueData(WorkspacePath workspacePath) {
      this.workspacePath = workspacePath;
    }
  }

  /** Verifies the project view. Any errors are output to the context as issues. */
  public static boolean verifyProjectView(
      BlazeContext context,
      WorkspaceRoot workspaceRoot,
      ProjectViewSet projectViewSet,
      WorkspaceLanguageSettings workspaceLanguageSettings) {
    if (!verifyIncludedPackagesExistOnDisk(context, workspaceRoot, projectViewSet)) {
      return false;
    }
    if (!verifyIncludedPackagesAreNotExcluded(context, projectViewSet)) {
      return false;
    }
    for (BlazeSyncPlugin syncPlugin : BlazeSyncPlugin.EP_NAME.getExtensions()) {
      if (!syncPlugin.validateProjectView(context, projectViewSet, workspaceLanguageSettings)) {
        return false;
      }
    }
    if (!projectViewSet.listItems(ExcludedSourceSection.KEY).isEmpty()) {
      IssueOutput.warn("excluded_sources is deprecated and has no effect.")
          .inFile(projectViewSet.getTopLevelProjectViewFile().projectViewFile)
          .submit(context);
    }
    return true;
  }

  private static boolean verifyIncludedPackagesAreNotExcluded(
      BlazeContext context, ProjectViewSet projectViewSet) {
    boolean ok = true;

    List<WorkspacePath> includedDirectories = getIncludedDirectories(projectViewSet);

    for (WorkspacePath includedDirectory : includedDirectories) {
      for (ProjectViewSet.ProjectViewFile projectViewFile : projectViewSet.getProjectViewFiles()) {
        List<DirectoryEntry> directoryEntries = Lists.newArrayList();
        for (ListSection<DirectoryEntry> section :
            projectViewFile.projectView.getSectionsOfType(DirectorySection.KEY)) {
          directoryEntries.addAll(section.items());
        }

        for (DirectoryEntry entry : directoryEntries) {
          if (entry.included) {
            continue;
          }

          WorkspacePath excludedDirectory = entry.directory;
          if (FileUtil.isAncestor(
              excludedDirectory.relativePath(), includedDirectory.relativePath(), false)) {
            IssueOutput.error(
                    String.format(
                        "%s is included, but that contradicts %s which was excluded",
                        includedDirectory.toString(), excludedDirectory.toString()))
                .inFile(projectViewFile.projectViewFile)
                .submit(context);
            ok = false;
          }
        }
      }
    }
    return ok;
  }

  private static List<WorkspacePath> getIncludedDirectories(ProjectViewSet projectViewSet) {
    List<WorkspacePath> includedDirectories = Lists.newArrayList();
    for (DirectoryEntry entry : projectViewSet.listItems(DirectorySection.KEY)) {
      if (entry.included) {
        includedDirectories.add(entry.directory);
      }
    }
    return includedDirectories;
  }

  private static boolean verifyIncludedPackagesExistOnDisk(
      BlazeContext context, WorkspaceRoot workspaceRoot, ProjectViewSet projectViewSet) {
    boolean ok = true;

    WorkspaceScanner workspaceScanner = WorkspaceScanner.getInstance();

    for (ProjectViewSet.ProjectViewFile projectViewFile : projectViewSet.getProjectViewFiles()) {
      List<DirectoryEntry> directoryEntries = Lists.newArrayList();
      for (ListSection<DirectoryEntry> section :
          projectViewFile.projectView.getSectionsOfType(DirectorySection.KEY)) {
        directoryEntries.addAll(section.items());
      }
      for (DirectoryEntry entry : directoryEntries) {
        if (!entry.included) {
          continue;
        }
        WorkspacePath workspacePath = entry.directory;
        if (!workspaceScanner.exists(workspaceRoot, workspacePath)) {
          IssueOutput.error(
                  String.format(
                      "Directory '%s' specified in import roots not found "
                          + "under workspace root '%s'",
                      workspacePath, workspaceRoot))
              .inFile(projectViewFile.projectViewFile)
              .withData(new MissingDirectoryIssueData(workspacePath))
              .submit(context);
          ok = false;
        }
      }
    }
    return ok;
  }
}
