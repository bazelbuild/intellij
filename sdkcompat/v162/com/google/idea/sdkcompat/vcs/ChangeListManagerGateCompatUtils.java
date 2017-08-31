package com.google.idea.sdkcompat.vcs;

import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.ChangeListManagerGate;

/**
 * Works around b/63888111 / <a href="https://youtrack.jetbrains.com/issue/IJSDK-284">IJSDK-284</a>,
 * which breaks support for {@link ChangeListManagerGate#editName(String, String)} in some SDK
 * versions. <br>
 * <br>
 * The methods in this class call the corresponding methods on the {@link ChangeListManagerGate}
 * instead of the {@link ChangeListManager}. It is normal to use this during a VCS update.
 */
public final class ChangeListManagerGateCompatUtils {
  private ChangeListManagerGateCompatUtils() {}

  public static void editName(
      ChangeListManagerGate addGate,
      ChangeListManager changeListManager,
      String oldName,
      String newName) {
    addGate.editName(oldName, newName);
  }

  public static void editComment(
      ChangeListManagerGate addGate,
      ChangeListManager changeListManager,
      String name,
      String newComment) {
    addGate.editComment(name, newComment);
  }
}
