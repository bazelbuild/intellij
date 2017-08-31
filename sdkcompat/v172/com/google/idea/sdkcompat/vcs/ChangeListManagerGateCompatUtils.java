package com.google.idea.sdkcompat.vcs;

import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.ChangeListManagerGate;

/**
 * Works around b/63888111 / <a href="https://youtrack.jetbrains.com/issue/IJSDK-284">IJSDK-284</a>,
 * which breaks support for {@link ChangeListManagerGate#editName(String, String)} in some SDK
 * versions. <br>
 * <br>
 * The methods in this class call the corresponding methods on the {@link ChangeListManager} instead
 * of the {@link ChangeListManagerGate}. Normally in a VCS update, the latter should be used. Key
 * known differences between the two are:
 *
 * <ul>
 *   <li>During the update, the manager and gate have independent state, and thus modifications made
 *       via one are not visible to the other.
 *   <li>If the VCS update completes successfully, the gate's state becomes authoritative, and any
 *       changes made via the manager during the update are applied on top of the gate's state.
 *   <li>If the VCS update fails, the manager's state remains authoritative, and the gate's state
 *       (and modifications) are discarded.
 * </ul>
 */
public final class ChangeListManagerGateCompatUtils {
  private ChangeListManagerGateCompatUtils() {}

  public static void editName(
      ChangeListManagerGate addGate,
      ChangeListManager changeListManager,
      String oldName,
      String newName) {
    changeListManager.editName(oldName, newName);
  }

  public static void editComment(
      ChangeListManagerGate addGate,
      ChangeListManager changeListManager,
      String name,
      String newComment) {
    changeListManager.editComment(name, newComment);
  }
}
