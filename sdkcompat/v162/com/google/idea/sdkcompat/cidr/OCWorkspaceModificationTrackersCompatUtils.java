package com.google.idea.sdkcompat.cidr;

import com.intellij.openapi.project.Project;
import com.jetbrains.cidr.lang.workspace.OCWorkspaceModificationTrackers;
import java.util.HashMap;
import java.util.Map;

/** Handles changes to modification trackers between our supported versions. */
public class OCWorkspaceModificationTrackersCompatUtils {

  private static final Map<Project, OCWorkspaceModificationTrackers> trackers = new HashMap<>();

  public static OCWorkspaceModificationTrackers getTrackers(Project project) {
    return trackers.computeIfAbsent(project, OCWorkspaceModificationTrackers::new);
  }

  /** Must be called inside a write action, on the EDT. */
  public static void incrementModificationCounts(Project project) {
    OCWorkspaceModificationTrackers modTrackers = getTrackers(project);
    modTrackers.getProjectFilesListTracker().incModificationCount();
    modTrackers.getSourceFilesListTracker().incModificationCount();
    modTrackers.getBuildConfigurationChangesTracker().incModificationCount();
    modTrackers.getBuildSettingsChangesTracker().incModificationCount();
  }
}
