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
package com.google.idea.blaze.plugin;

import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.sdkcompat.clion.CMakeNotificationProviderWrapper;
import com.google.idea.sdkcompat.general.EditorNotificationCompat;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.EditorNotifications;

import javax.annotation.Nullable;
import javax.swing.JComponent;

/** Need to filter out CMake messages if we are a Blaze project. */
public class CMakeNotificationFilter extends EditorNotifications.Provider<JComponent>
    implements DumbAware {
  private final CMakeNotificationProviderWrapper delegate;

  private static final Key<JComponent> KEY = Key.create("CMakeNotificationFilter");

  private CMakeNotificationFilter(Project project) {
    this.delegate = Blaze.isBlazeProject(project) ? null : new CMakeNotificationProviderWrapper();
  }

  @Override
  public Key<JComponent> getKey() {
    return KEY;
  }

  @Nullable
  @Override
  public JComponent createNotificationPanel(
      VirtualFile virtualFile, FileEditor fileEditor, Project project) {
    return delegate == null
        ? null
        : delegate.createNotificationPanel(virtualFile, fileEditor, project);
  }

  public static void overrideProjectExtension(Project project) {
    CMakeNotificationProviderWrapper.unregisterDelegateExtension(EditorNotificationCompat.getEp(project));
    EditorNotificationCompat.getEp(project).registerExtension(new CMakeNotificationFilter(project));
  }
}
