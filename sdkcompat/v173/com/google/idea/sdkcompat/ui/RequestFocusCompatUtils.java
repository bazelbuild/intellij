/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
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
package com.google.idea.sdkcompat.ui;

import com.intellij.ui.EditorTextField;
import java.awt.Component;

/** Works around b/64290580 which affects certain SDK versions. */
public final class RequestFocusCompatUtils {
  private RequestFocusCompatUtils() {}

  /**
   * Focuses the given component.
   *
   * @see Component#requestFocus()
   */
  public static void requestFocus(Component component) {
    if (component instanceof EditorTextField && ((EditorTextField) component).getEditor() == null) {
      // If the editor is null, requestFocus() will just indirectly call requestFocus(),
      // until the stack overflows. Instead, just don't support focusing the editor.
      return;
    }
    component.requestFocus();
  }
}
