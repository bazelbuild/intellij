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

package com.google.idea.blaze.base.ide;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.idea.blaze.base.actions.BlazeProjectAction;
import com.google.idea.blaze.base.bazel.BuildSystemProvider;
import com.google.idea.blaze.base.buildmodifier.BuildFileModifier;
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.projectview.ProjectViewManager;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.Scope;
import com.google.idea.blaze.base.scope.output.PrintOutput;
import com.google.idea.blaze.base.scope.output.StatusOutput;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.sync.projectview.ImportRoots;
import com.google.idea.blaze.base.util.WorkspacePathUtil;
import com.intellij.history.LocalHistory;
import com.intellij.history.LocalHistoryAction;
import com.intellij.ide.IdeView;
import com.intellij.ide.util.DirectoryChooserUtil;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.PlatformIcons;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.jetbrains.annotations.NotNull;

class NewBlazePackageAction extends BlazeProjectAction implements DumbAware {
  private static final Logger logger = Logger.getInstance(NewBlazePackageAction.class);

  public NewBlazePackageAction() {
    super();
  }

  @Override
  protected void actionPerformedInBlazeProject(Project project, AnActionEvent event) {
    final IdeView view = event.getData(LangDataKeys.IDE_VIEW);
    Scope.root(
        context -> {
          if (view == null || project == null) {
            return;
          }
          PsiDirectory directory = getOrChooseDirectory(project, view);

          if (directory == null) {
            return;
          }

          NewBlazePackageDialog newBlazePackageDialog =
              new NewBlazePackageDialog(project, directory);
          boolean isOk = newBlazePackageDialog.showAndGet();
          if (!isOk) {
            return;
          }

          final Label newRule = newBlazePackageDialog.getNewRule();
          final Kind newRuleKind = newBlazePackageDialog.getNewRuleKind();
          // If we returned OK, we should have a non null result
          logger.assertTrue(newRule != null);
          logger.assertTrue(newRuleKind != null);

          context.output(
              new StatusOutput(
                  String.format("Setting up a new %s package", Blaze.buildSystemName(project))));

          Optional<VirtualFile> virtualFile =
              createPackageOnDisk(project, context, newRule, newRuleKind);
          if (!virtualFile.isPresent()) {
            return;
          }
          PsiFile psiFile = PsiManager.getInstance(project).findFile(virtualFile.get());
          view.selectElement(psiFile);
        });
  }

  private Optional<VirtualFile> createPackageOnDisk(
      Project project, BlazeContext context, Label newRule, Kind ruleKind) {
    BuildSystemProvider provider =
        checkNotNull(BuildSystemProvider.getBuildSystemProvider(Blaze.getBuildSystem(project)));
    String buildFileName = provider.possibleBuildFileNames().get(0);

    String commandName =
        String.format(
            "Creating %s package: %s", Blaze.buildSystemName(project), newRule.toString());

    return new WriteCommandAction<Optional<VirtualFile>>(project, commandName) {

      @Override
      protected void run(@NotNull Result<Optional<VirtualFile>> result) throws Throwable {
        LocalHistory localHistory = LocalHistory.getInstance();
        LocalHistoryAction action = localHistory.startAction(commandName);

        try {
          WorkspaceRoot workspaceRoot = WorkspaceRoot.fromProject(project);
          File dir = workspaceRoot.fileForPath(newRule.blazePackage());
          try {
            VirtualFile newDirectory = VfsUtil.createDirectories(dir.getPath());
            VirtualFile newFile = newDirectory.createChildData(this, buildFileName);
            BuildFileModifier buildFileModifier = BuildFileModifier.getInstance();
            buildFileModifier.addRule(project, newRule, ruleKind);
            result.setResult(Optional.of(newFile));
          } catch (IOException e) {
            String errorMessage = "Error creating new package: " + e.getMessage();
            context.output(PrintOutput.error(errorMessage));
            logger.warn("Error creating new package", e);
            result.setResult(Optional.empty());
          }
        } finally {
          action.finish();
        }
      }
    }.execute().getResultObject();
  }

  @Override
  protected void updateForBlazeProject(Project project, AnActionEvent event) {
    Presentation presentation = event.getPresentation();
    if (isEnabled(event)) {
      String text = String.format("New %s Package", Blaze.buildSystemName(project));
      presentation.setEnabledAndVisible(true);
      presentation.setText(text);
      presentation.setDescription(text);
      presentation.setIcon(PlatformIcons.PACKAGE_ICON);
    } else {
      presentation.setEnabledAndVisible(false);
    }
  }

  private boolean isEnabled(AnActionEvent event) {
    Project project = event.getProject();
    IdeView view = event.getData(LangDataKeys.IDE_VIEW);
    if (project == null || view == null) {
      return false;
    }

    List<PsiDirectory> directories = filterDirectories(project, view.getDirectories());
    if (directories.isEmpty()) {
      return false;
    }

    return true;
  }

  /** Filter out directories that do not live under the project's directories. */
  private static List<PsiDirectory> filterDirectories(Project project, PsiDirectory[] directories) {
    if (directories.length == 0) {
      return ImmutableList.of();
    }
    ProjectViewSet projectViewSet = ProjectViewManager.getInstance(project).getProjectViewSet();
    if (projectViewSet == null) {
      return ImmutableList.of();
    }
    WorkspaceRoot workspaceRoot = WorkspaceRoot.fromProject(project);
    ImportRoots importRoots =
        ImportRoots.builder(workspaceRoot, Blaze.getBuildSystem(project))
            .add(projectViewSet)
            .build();
    return Lists.newArrayList(directories)
        .stream()
        .filter(directory -> isUnderProjectViewDirectory(workspaceRoot, importRoots, directory))
        .collect(Collectors.toList());
  }

  private static boolean isUnderProjectViewDirectory(
      WorkspaceRoot workspaceRoot, ImportRoots importRoots, PsiDirectory directory) {
    VirtualFile virtualFile = directory.getVirtualFile();
    // Ignore jars, etc. and their contents, which are in an ArchiveFileSystem.
    if (!(virtualFile.isInLocalFileSystem())) {
      return false;
    }
    if (!workspaceRoot.isInWorkspace(virtualFile)) {
      return false;
    }
    WorkspacePath workspacePath = workspaceRoot.workspacePathFor(virtualFile);
    return WorkspacePathUtil.isUnderAnyWorkspacePath(importRoots.rootDirectories(), workspacePath);
  }

  @Nullable
  private static PsiDirectory getOrChooseDirectory(Project project, IdeView view) {
    List<PsiDirectory> dirs = filterDirectories(project, view.getDirectories());
    if (dirs.size() == 0) {
      return null;
    }
    if (dirs.size() == 1) {
      return dirs.get(0);
    } else {
      return DirectoryChooserUtil.selectDirectory(
          project, dirs.toArray(new PsiDirectory[dirs.size()]), null, "");
    }
  }
}
