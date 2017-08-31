package com.google.idea.sdkcompat.profile;

import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.openapi.project.Project;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;

/** Utility to bridge different SDK versions. */
public class InspectionProfileUtil {

  public static void disableTool(String toolName, Project project) {
    InspectionProfileImpl profile =
        InspectionProjectProfileManager.getInstance(project).getCurrentProfile();
    profile.setToolEnabled(toolName, false, project);
  }
}
