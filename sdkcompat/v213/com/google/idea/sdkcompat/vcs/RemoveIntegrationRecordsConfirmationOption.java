package com.google.idea.sdkcompat.vcs;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsShowConfirmationOption;
import com.intellij.openapi.vcs.VcsShowConfirmationOption.Value;
import com.intellij.openapi.vcs.VcsShowSettingOption;

/** Helper to show a confirmation dialog for whether to remove integration records. */
public class RemoveIntegrationRecordsConfirmationOption implements VcsShowConfirmationOption {

  private final VcsShowSettingOption showConfirmation;

  public RemoveIntegrationRecordsConfirmationOption(Project project, AbstractVcs vcs) {
    showConfirmation =
        ProjectLevelVcsManager.getInstance(project)
            .getOrCreateCustomOption("Remove Integration Records", vcs);
  }

  @Override
  public Value getValue() {
    return showConfirmation.getValue() ? Value.SHOW_CONFIRMATION : Value.DO_ACTION_SILENTLY;
  }

  @Override
  public void setValue(Value value) {
    if (value.equals(Value.DO_NOTHING_SILENTLY)) {
      // This happens if the user checks "don't ask again", but clicks No or cancels.
      // We don't want to persist that, since the action would just become useless.
      return;
    }
    showConfirmation.setValue(value.equals(Value.SHOW_CONFIRMATION));
  }

  @Override
  public boolean isPersistent() {
    return true;
  }
}
