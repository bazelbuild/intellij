/*
 * Copyright 2023 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.qsync.action;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.base.logging.utils.querysync.QuerySyncActionStatsScope;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.qsync.QuerySyncManager;
import com.google.idea.blaze.base.qsync.TargetsToBuild;
import com.google.idea.blaze.base.sync.status.BlazeSyncStatus;
import com.google.idea.blaze.common.Label;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.awt.RelativePoint;
import java.awt.event.MouseEvent;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Helper class for actions that build dependencies for source files, to allow the core logic to be
 * shared.
 */
public class BuildDependenciesHelper {

  /** Encapsulates a relative position to show the target selection popup at. */
  public interface PopupPosititioner {
    void showInCorrectPosition(JBPopup popup);

    /**
     * Shows the popup at the location of the mount event, or centered it the screen if the event is
     * not a mouse event (e.g. keyboard shortcut).
     *
     * <p>This is used e.g. when selecting "build dependencies" from a context menu.
     */
    static PopupPosititioner showAtMousePointerOrCentered(AnActionEvent e) {
      return popup -> {
        if (e.getInputEvent() instanceof MouseEvent) {
          popup.show(
              RelativePoint.fromScreen(((MouseEvent) e.getInputEvent()).getLocationOnScreen()));
        } else {
          popup.showCenteredInCurrentWindow(e.getProject());
        }
      };
    }

    /**
     * Shows the popup underneath the clicked UI component, or centered in tge screen if the event
     * is not a mouse event.
     *
     * <p>This is used to show the popup underneath the inspection widget action.
     */
    static PopupPosititioner showUnderneathClickedComponentOrCentered(AnActionEvent event) {
      return popup -> {
        if (event.getInputEvent() instanceof MouseEvent
            && event.getInputEvent().getComponent() != null) {
          // if the user clicked the action button, show underneath that
          popup.showUnderneathOf(event.getInputEvent().getComponent());
        } else {
          popup.showCenteredInCurrentWindow(event.getProject());
        }
      };
    }
  }

  private final Project project;
  private final QuerySyncManager syncManager;
  private final Class<?> actionClass;

  public BuildDependenciesHelper(Project project, Class<?> actionClass) {
    this.project = project;
    this.syncManager = QuerySyncManager.getInstance(project);
    this.actionClass = actionClass;
  }

  boolean canEnableAnalysisNow() {
    return !BlazeSyncStatus.getInstance(project).syncInProgress();
  }

  public TargetsToBuild getTargetsToEnableAnalysisFor(VirtualFile virtualFile) {
    if (!syncManager.isProjectLoaded() || BlazeSyncStatus.getInstance(project).syncInProgress()) {
      return TargetsToBuild.NONE;
    }
    return syncManager.getTargetsToBuild(virtualFile);
  }

  public int getSourceFileMissingDepsCount(TargetsToBuild toBuild) {
    Preconditions.checkState(toBuild.type() == TargetsToBuild.Type.SOURCE_FILE);
    return syncManager.getDependencyTracker().getPendingExternalDeps(toBuild.targets()).size();
  }

  public Optional<Path> getRelativePathToEnableAnalysisFor(VirtualFile virtualFile) {
    if (virtualFile == null || !virtualFile.isInLocalFileSystem()) {
      return Optional.empty();
    }
    Path workspaceRoot = WorkspaceRoot.fromProject(project).path();
    Path filePath = virtualFile.toNioPath();
    if (!filePath.startsWith(workspaceRoot)) {
      return Optional.empty();
    }

    Path relative = workspaceRoot.relativize(filePath);
    if (!syncManager.canEnableAnalysisFor(relative)) {
      return Optional.empty();
    }
    return Optional.of(relative);
  }

  public static VirtualFile getVirtualFile(AnActionEvent e) {
    return e.getData(CommonDataKeys.VIRTUAL_FILE);
  }

  public void enableAnalysis(AnActionEvent e, PopupPosititioner positioner) {
    VirtualFile vfile = getVirtualFile(e);
    TargetsToBuild toBuild = getTargetsToEnableAnalysisFor(vfile);
    if (toBuild.isEmpty()) {
      return;
    }
    QuerySyncActionStatsScope querySyncActionStats =
        new QuerySyncActionStatsScope(actionClass, e, vfile);
    if (!toBuild.isAmbiguous()) {
      syncManager.enableAnalysis(toBuild.targets(), querySyncActionStats);
      return;
    }
    chooseTargetToBuildFor(
        vfile,
        toBuild,
        positioner,
        label -> enableAnalysis(ImmutableSet.of(label), querySyncActionStats));
  }

  void enableAnalysis(ImmutableSet<Label> targets, QuerySyncActionStatsScope querySyncActionStats) {
    syncManager.enableAnalysis(targets, querySyncActionStats);
  }

  public void chooseTargetToBuildFor(
      VirtualFile vfile,
      TargetsToBuild toBuild,
      PopupPosititioner positioner,
      Consumer<Label> chosenConsumer) {
    JBPopupFactory factory = JBPopupFactory.getInstance();
    ListPopup popup =
        factory.createListPopup(SelectTargetPopupStep.create(toBuild, vfile, chosenConsumer));
    positioner.showInCorrectPosition(popup);
  }

  static class SelectTargetPopupStep extends BaseListPopupStep<Label> {
    static SelectTargetPopupStep create(
        TargetsToBuild toBuild, VirtualFile forFile, Consumer<Label> onChosen) {
      ImmutableList<Label> rows =
          ImmutableList.sortedCopyOf(Comparator.comparing(Label::toString), toBuild.targets());

      return new SelectTargetPopupStep(rows, forFile.getName(), onChosen);
    }

    private final Consumer<Label> onChosen;

    SelectTargetPopupStep(ImmutableList<Label> rows, String forFileName, Consumer<Label> onChosen) {
      super("Select target to build for " + forFileName, rows);
      this.onChosen = onChosen;
    }

    @Override
    public PopupStep<?> onChosen(Label selectedValue, boolean finalChoice) {
      if (selectedValue == null) {
        return FINAL_CHOICE;
      }
      if (finalChoice) {
        onChosen.accept(selectedValue);
      }
      return FINAL_CHOICE;
    }
  }
}
