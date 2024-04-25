/*
 * Copyright 2024 The Bazel Authors. All rights reserved.
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

package com.google.idea.blaze.base.wizard2;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.Service.Level;
import com.intellij.openapi.components.State;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.EditorNotifications;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;

@Service(Level.PROJECT)
@State(name = "BazelDisableImportNotification")
public final class BazelDisableImportNotification implements PersistentStateComponent<BazelDisableImportNotification> {
  public final static class Action extends AnAction {
    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
      final Project project = event.getProject();
      if (project == null) return;

      setNotificationDisabled(project);
      reloadEditorNotifications(project);
    }
  }

  public boolean disabled = false;

  @Override
  public BazelDisableImportNotification getState() {
    return this;
  }

  @Override
  public void loadState(@NotNull BazelDisableImportNotification state) {
    XmlSerializerUtil.copyBean(state, this);
  }

  public static boolean isNotificationDisabled(Project project) {
    return project.getService(BazelDisableImportNotification.class).disabled;
  }

  private static void setNotificationDisabled(Project project) {
    project.getService(BazelDisableImportNotification.class).disabled = true;
  }

  private static void reloadEditorNotifications(Project project) {
    final VirtualFile[] openFiles = FileEditorManager.getInstance(project).getOpenFiles();

    for (final VirtualFile file : openFiles) {
      EditorNotifications.getInstance(project).updateNotifications(file);
    }
  }
}
