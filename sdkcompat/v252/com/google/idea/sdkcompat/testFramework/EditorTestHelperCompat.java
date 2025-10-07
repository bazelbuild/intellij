package com.google.idea.sdkcompat.testFramework;

import com.google.common.collect.ImmutableList;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.testFramework.EditorTestUtil;
import com.intellij.testFramework.EditorTestUtil.CaretAndSelectionState;
import com.intellij.testFramework.EditorTestUtil.CaretInfo;
import com.intellij.testFramework.EdtTestUtil;

public class EditorTestHelperCompat {

  public void setCaretPosition(Editor editor, int lineNumber, int columnNumber) throws Throwable {
    final CaretInfo info = new CaretInfo(new LogicalPosition(lineNumber, columnNumber), null);
    EdtTestUtil.runInEdtAndWait(
        () ->
            EditorTestUtil.setCaretsAndSelection(
                editor, new CaretAndSelectionState(ImmutableList.of(info), null)));
  }

  public void assertCaretPosition(Editor editor, int lineNumber, int columnNumber) {
    CaretInfo info = new CaretInfo(new LogicalPosition(lineNumber, columnNumber), null);
    EditorTestUtil.verifyCaretAndSelectionState(
        editor, new CaretAndSelectionState(ImmutableList.of(info), null));
  }

}