package com.google.idea.sdkcompat.ui;

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
    component.requestFocus();
  }
}
