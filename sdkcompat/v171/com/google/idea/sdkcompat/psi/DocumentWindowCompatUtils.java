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
package com.google.idea.sdkcompat.psi;

import com.intellij.injected.editor.DocumentWindowImpl;
import com.intellij.openapi.editor.Document;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.tree.injected.Place;

/** Adapter to bridge different SDK versions. */
public class DocumentWindowCompatUtils {

  /**
   * Copies shreds from the document backing newFile to the document backing originalFile, if:
   *
   * <ul>
   *   <li>newFile and originalFile both contain documents of type DocumentWindowImpl
   *   <li>newFile and originalFile don't already have the same shreds
   * </ul>
   *
   * <p>This works around an additional check added in:
   * https://github.com/JetBrains/intellij-community/commit/bfa51a87c6d04a7e3d81b8e5c100c5b8043c7fb8
   *
   * @param newFile the new file, likely retrieved from {@link #getValidFile(PsiFile)}.
   * @param originalFile the original, invalid file.
   */
  public static void copyShredsIfPossible(PsiFile newFile, PsiFile originalFile) {
    Document originalDocument = originalFile.getViewProvider().getDocument();
    Document newDocument = newFile.getViewProvider().getDocument();
    if (originalDocument instanceof DocumentWindowImpl
        && newDocument instanceof DocumentWindowImpl) {
      DocumentWindowImpl originalDocumentWindow = (DocumentWindowImpl) originalDocument;
      DocumentWindowImpl newDocumentWindow = (DocumentWindowImpl) newDocument;
      Place originalShreds = originalDocumentWindow.getShreds();
      Place newShreds = newDocumentWindow.getShreds();
      if (!originalShreds.equals(newShreds)) {
        originalDocumentWindow.setShreds(newShreds);
      }
    }
  }
}
