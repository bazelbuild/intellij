package com.google.idea.sdkcompat.cidr;

import com.intellij.openapi.project.Project;
import com.jetbrains.cidr.lang.workspace.OCWorkspaceModificationTrackers;

/** Handles changes to modification trackers between our supported versions. */
public class OCWorkspaceModificationTrackersCompatUtils {

  public static OCWorkspaceModificationTrackers getTrackers(Project project) {
    return OCWorkspaceModificationTrackers.getInstance(project);
  }

  /**
   * Causes symbol tables to be rebuilt and invalidates cidr caches attached to resolve
   * configurations.
   *
   * <p>Must be called inside a write action, on the EDT.
   */
  public static void incrementModificationCounts(Project project) {
    partialIncModificationCounts(project);
    OCWorkspaceModificationTrackers modTrackers = getTrackers(project);
    modTrackers.getBuildSettingsChangesTracker().incModificationCount();
  }

  /**
   * Does not trigger symbol table rebuilding, and only clears part of the cidr caches attached to
   * resolve configurations.
   *
   * <p>Must be called inside a write action, on the EDT.
   */
  public static void partialIncModificationCounts(Project project) {
    OCWorkspaceModificationTrackers modTrackers = getTrackers(project);
    modTrackers.getProjectFilesListTracker().incModificationCount();
    modTrackers.getSourceFilesListTracker().incModificationCount();
    modTrackers.getSelectedResolveConfigurationTracker().incModificationCount();
  }
}
