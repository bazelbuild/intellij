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
package com.google.idea.blaze.base.actions;

import static com.google.idea.blaze.base.actions.BlazeProjectAction.QuerySyncStatus.HIDDEN;
import static com.google.idea.blaze.base.actions.BlazeProjectAction.QuerySyncStatus.REQUIRED;
import static com.google.idea.blaze.base.actions.BlazeProjectAction.QuerySyncStatus.SUPPORTED;

import com.google.idea.blaze.base.qsync.QuerySync;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.BuildSystemName;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import javax.annotation.Nullable;
import javax.swing.Icon;

/** Base class action that hides for non-blaze projects. */
public abstract class BlazeProjectAction extends AnAction {
  /** Indicates if an action supports querysync. */
  protected enum QuerySyncStatus {
    /**
     * The action does not support querysync, and is not ever expected to. It's not visible in the
     * UI.
     */
    HIDDEN,
    /**
     * The action may support querysync in future, but does not yet. It is visible in the UI, but
     * disabled.
     */
    DISABLED,
    /** The action supports querysync and is available in the UI. */
    SUPPORTED,
    /** The action requires querysync in order to be usable. */
    REQUIRED,
  }

  protected BlazeProjectAction() {}

  protected BlazeProjectAction(Icon icon) {
    super(icon);
  }

  protected BlazeProjectAction(@Nullable String text) {
    super(text);
  }

  protected BlazeProjectAction(
      @Nullable String text, @Nullable String description, @Nullable Icon icon) {
    super(text, description, icon);
  }

  @Override
  public final void update(AnActionEvent e) {
    Project project = e.getProject();
    if (project == null || !Blaze.isBlazeProject(project)) {
      e.getPresentation().setEnabledAndVisible(false);
      return;
    }
    final QuerySyncStatus querySyncStatus = querySyncSupport();
    if (QuerySync.isEnabled()) {
      if (querySyncStatus == HIDDEN) {
        e.getPresentation().setEnabledAndVisible(false);
        return;
      }
    } else {
      if (querySyncStatus == REQUIRED) {
        e.getPresentation().setEnabledAndVisible(false);
        return;
      }
    }

    e.getPresentation().setEnabledAndVisible(true);

    if (!compatibleBuildSystem(project)) {
      e.getPresentation().setEnabled(false);
      return;
    }
    if (QuerySync.isEnabled() && querySyncStatus != SUPPORTED && querySyncStatus != REQUIRED) {
      // TODO(b/260643753) disabling all blaze actions for querysync is way too broad, instead we
      //  should investigate which can be supported and update them accordingly.
      e.getPresentation().setEnabled(false);
      return;
    }

    updateForBlazeProject(project, e);
  }

  @Override
  public final void actionPerformed(AnActionEvent anActionEvent) {
    Project project = anActionEvent.getProject();
    if (project == null) {
      return;
    }
    actionPerformedInBlazeProject(project, anActionEvent);
  }

  protected QuerySyncStatus querySyncSupport() {
    // Default to disabled, meaning that the action has not yet been updated for querysync.
    return QuerySyncStatus.DISABLED;
  }

  protected void updateForBlazeProject(Project project, AnActionEvent e) {}

  protected abstract void actionPerformedInBlazeProject(Project project, AnActionEvent e);

  private boolean compatibleBuildSystem(Project project) {
    BuildSystemName requiredBuildSystemName = requiredBuildSystem();
    if (requiredBuildSystemName == null) {
      return true;
    }
    return Blaze.getBuildSystemName(project) == requiredBuildSystemName;
  }

  @Nullable
  protected BuildSystemName requiredBuildSystem() {
    return null;
  }
}
