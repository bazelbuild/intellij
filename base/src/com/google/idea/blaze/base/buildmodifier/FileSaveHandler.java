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
package com.google.idea.blaze.base.buildmodifier;

import com.google.idea.blaze.base.bazel.BuildSystemProvider;
import com.google.idea.blaze.base.settings.BlazeUserSettings;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.DocumentRunnable;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileDocumentManagerAdapter;
import com.intellij.openapi.vfs.VirtualFile;

/** Runs the buildifier command on file save. */
public class FileSaveHandler extends FileDocumentManagerAdapter {

  @Override
  public void beforeDocumentSaving(final Document document) {
    if (!BlazeUserSettings.getInstance().getFormatBuildFilesOnSave() || !document.isWritable()) {
      return;
    }
    FileDocumentManager fileDocumentManager = FileDocumentManager.getInstance();
    VirtualFile file = fileDocumentManager.getFile(document);
    if (file == null || !file.isValid()) {
      return;
    }

    if (!isBuildFile(file)) {
      return;
    }
    int lines = document.getLineCount();
    if (lines > 0) {
      String text = document.getText();
      String formattedText = BuildFileFormatter.formatText(text);
      updateDocument(document, formattedText);
    }
  }

  private void updateDocument(final Document document, final String formattedContent) {
    ApplicationManager.getApplication()
        .runWriteAction(
            new DocumentRunnable(document, null) {
              @Override
              public void run() {
                CommandProcessor.getInstance()
                    .runUndoTransparentAction(() -> document.setText(formattedContent));
              }
            });
  }

  private static boolean isBuildFile(VirtualFile file) {
    return BuildSystemProvider.defaultBuildSystem().isBuildFile(file.getName());
  }
}
