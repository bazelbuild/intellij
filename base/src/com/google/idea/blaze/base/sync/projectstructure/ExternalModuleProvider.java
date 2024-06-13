package com.google.idea.blaze.base.sync.projectstructure;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.module.Module;

/**
 * An EP provider for external modules.
 */
public interface ExternalModuleProvider {
    ExtensionPointName<ExternalModuleProvider> EP_NAME =
            ExtensionPointName.create("com.google.idea.blaze.base.sync.projectstructure.ExternalModuleProvider");

    boolean isOwnedByExternalPlugin(Module module);
}
