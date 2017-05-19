package com.google.idea.sdkcompat.cidr;

import com.intellij.openapi.project.Project;
import com.jetbrains.cidr.lang.workspace.OCResolveConfiguration;
import com.jetbrains.cidr.lang.workspace.OCWorkspace;
import com.jetbrains.cidr.lang.workspace.OCWorkspaceModificationTrackers;
import javax.annotation.Nullable;

/** Adapter to bridge different SDK versions. */
public abstract class OCWorkspaceAdapter implements OCWorkspace {

  private final Project project;

  protected OCWorkspaceAdapter(Project project) {
    this.project = project;
  }

  @Nullable
  @Override
  public OCResolveConfiguration getSelectedResolveConfiguration() {
    return null;
  }

  @Override
  public OCWorkspaceModificationTrackers getModificationTrackers() {
    return OCWorkspaceModificationTrackersCompatUtils.getTrackers(project);
  }
}
