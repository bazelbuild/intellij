package com.google.idea.sdkcompat.cidr;

import com.intellij.openapi.project.Project;
import com.jetbrains.cidr.lang.workspace.OCWorkspace;

/** Adapter to bridge different SDK versions. */
public abstract class OCWorkspaceAdapter implements OCWorkspace {
  protected OCWorkspaceAdapter(Project project) {}
}
