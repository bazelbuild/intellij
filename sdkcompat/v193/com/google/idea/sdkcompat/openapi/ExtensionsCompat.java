package com.google.idea.sdkcompat.openapi;

import com.intellij.openapi.Disposable;

/** Extensions.cleanRootArea removed in 193.* #api192 */
public class ExtensionsCompat {
  public static void cleanRootArea(Disposable disposable) {
    // do nothing
  }
}
