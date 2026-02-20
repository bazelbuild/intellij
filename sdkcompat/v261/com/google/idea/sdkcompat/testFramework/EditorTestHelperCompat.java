/*
 * Copyright 2026 The Bazel Authors. All rights reserved.
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

package com.google.idea.sdkcompat.testFramework;

import com.google.common.collect.ImmutableList;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.testFramework.common.EditorCaretTestUtil.CaretAndSelectionState;
import com.intellij.testFramework.common.EditorCaretTestUtil.CaretInfo;
import com.intellij.testFramework.EditorTestUtil;
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
