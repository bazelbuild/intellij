package com.google.idea.blaze.cpp;

import com.intellij.openapi.module.Module;
import com.jetbrains.cidr.project.workspace.CidrOwnModuleDetector;
import org.jetbrains.annotations.NotNull;

import static com.google.idea.blaze.base.settings.Blaze.isBlazeProject;

public class BlazeOwnModuleDetector implements CidrOwnModuleDetector {
    @Override
    public boolean isOwnModule(@NotNull Module module) {
        return isBlazeProject(module.getProject());
    }
}
