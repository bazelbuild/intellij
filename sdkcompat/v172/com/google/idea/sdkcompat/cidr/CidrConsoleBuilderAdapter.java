package com.google.idea.sdkcompat.cidr;

import com.intellij.openapi.project.Project;
import com.jetbrains.cidr.execution.CidrConsoleBuilder;

/** Adapter to bridge different SDK versions. */
public class CidrConsoleBuilderAdapter extends CidrConsoleBuilder {

  public CidrConsoleBuilderAdapter(Project project) {
    super(project, null, null);
  }
}
