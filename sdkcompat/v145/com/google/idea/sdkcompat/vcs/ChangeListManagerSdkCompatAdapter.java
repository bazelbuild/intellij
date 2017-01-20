package com.google.idea.sdkcompat.vcs;

import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.util.continuation.ContinuationPause;

/** SDK adapter for change list interface. */
public abstract class ChangeListManagerSdkCompatAdapter extends ChangeListManager {
  @Override
  public void freeze(ContinuationPause context, String reason) {
    throw new UnsupportedOperationException("ChangeListManager#freeze()");
  }
}
