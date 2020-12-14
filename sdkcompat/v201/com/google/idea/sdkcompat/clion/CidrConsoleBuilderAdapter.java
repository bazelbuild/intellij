package com.google.idea.sdkcompat.clion;

import com.intellij.openapi.project.Project;
import com.jetbrains.cidr.execution.CidrConsoleBuilder;
import java.nio.file.Path;

/** Api compat with 2020.2 #api201 */
public class CidrConsoleBuilderAdapter extends CidrConsoleBuilder {
  public CidrConsoleBuilderAdapter(Project project) {
    super(project, null, (Path) null);
  }
}
