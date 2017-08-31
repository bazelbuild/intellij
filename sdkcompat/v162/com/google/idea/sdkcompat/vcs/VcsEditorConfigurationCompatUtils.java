package com.google.idea.sdkcompat.vcs;

import com.google.common.collect.ImmutableList;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.spellchecker.ui.SpellCheckingEditorCustomization;
import com.intellij.ui.EditorCustomization;
import com.intellij.ui.RightMarginEditorCustomization;
import java.util.List;

/** Handles VCS changelist editor customizations that differ between SDK versions. */
public class VcsEditorConfigurationCompatUtils {

  public static List<EditorCustomization> getVcsConfigurationCustomizations(
      Project project, VcsConfiguration config) {
    return ImmutableList.of(
        SpellCheckingEditorCustomization.getInstance(config.CHECK_COMMIT_MESSAGE_SPELLING),
        new RightMarginEditorCustomization(
            config.USE_COMMIT_MESSAGE_MARGIN, config.COMMIT_MESSAGE_MARGIN_SIZE));
  }
}
