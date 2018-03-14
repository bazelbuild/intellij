package com.google.idea.sdkcompat.psi;

import com.intellij.psi.PsiFile;

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
    // shreds no longer accessible in 2018.1
  }
}
