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
