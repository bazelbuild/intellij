package com.google.idea.sdkcompat.profile;

import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.openapi.project.Project;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;

/** Utility to bridge different SDK versions. */
public class InspectionProfileUtil {

  public static void disableTool(String toolName, Project project) {
    InspectionProfileImpl profile =
        (InspectionProfileImpl)
            InspectionProjectProfileManager.getInstance(project).getInspectionProfile();
    profile.disableTool(toolName, project);
  }
}
