package com.google.idea.blaze.cpp;

import com.google.idea.sdkcompat.cpp.CidrOwnModuleDetectorWrapper;
import com.intellij.openapi.module.Module;
import org.jetbrains.annotations.NotNull;

import static com.google.idea.blaze.base.settings.Blaze.isBlazeProject;

public class BlazeOwnModuleDetector implements CidrOwnModuleDetectorWrapper {
    @Override
    public boolean isOwnModule(@NotNull Module module) {
        return isBlazeProject(module.getProject());
    }
}
