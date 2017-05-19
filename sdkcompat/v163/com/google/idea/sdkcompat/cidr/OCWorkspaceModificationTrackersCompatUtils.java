package com.google.idea.sdkcompat.cidr;

import com.intellij.openapi.project.Project;
import com.jetbrains.cidr.lang.workspace.OCWorkspaceModificationTrackers;

/** Handles changes to modification trackers between our supported versions. */
public class OCWorkspaceModificationTrackersCompatUtils {

  public static OCWorkspaceModificationTrackers getTrackers(Project project) {
    return OCWorkspaceModificationTrackers.getInstance(project);
  }

  /** Must be called inside a write action, on the EDT. */
  public static void incrementModificationCounts(Project project) {
    OCWorkspaceModificationTrackers modTrackers = getTrackers(project);
    modTrackers.getProjectFilesListTracker().incModificationCount();
    modTrackers.getSourceFilesListTracker().incModificationCount();
    modTrackers.getSelectedResolveConfigurationTracker().incModificationCount();
    modTrackers.getBuildSettingsChangesTracker().incModificationCount();
  }
}
