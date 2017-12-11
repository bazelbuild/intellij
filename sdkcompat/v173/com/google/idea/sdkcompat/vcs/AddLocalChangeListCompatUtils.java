package com.google.idea.sdkcompat.vcs;

import com.intellij.openapi.vcs.changes.ChangeListData;
import com.intellij.openapi.vcs.changes.ChangeListManagerEx;
import com.intellij.openapi.vcs.changes.LocalChangeList;

/**
 * SDK adapter for creating {@link LocalChangeList LocalChangeLists} corresponding to remote
 * changelists.
 */
public final class AddLocalChangeListCompatUtils {

  /**
   * Creates a local changelist marked with custom data, so our {@link
   * com.intellij.openapi.vcs.changes.ChangeListListener} knows this list already has a remote
   * changelist. This operation will trigger the listener on the VCS Pane updater thread.
   */
  public static LocalChangeList createForRemoteChangeList(
      ChangeListManagerEx changeListManagerEx, String name, String description) {
    return changeListManagerEx.addChangeList(name, description, new RemoteChangeListData());
  }

  /**
   * Determines whether the given list was created for an existing remote changelist, even if it has
   * not been mapped in the changelist synchronizer yet.
   */
  public static boolean wasCreatedForRemoteChangeList(LocalChangeList localChangeList) {
    return localChangeList.getData() instanceof RemoteChangeListData;
  }

  private static class RemoteChangeListData extends ChangeListData {}
}
