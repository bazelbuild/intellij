package com.google.idea.sdkcompat.cpp;

import com.intellij.openapi.module.Module;
import org.jetbrains.annotations.NotNull;

/**
 * #api222
 */
public interface CidrOwnModuleDetectorWrapper {
    boolean isOwnModule(@NotNull Module module);
}