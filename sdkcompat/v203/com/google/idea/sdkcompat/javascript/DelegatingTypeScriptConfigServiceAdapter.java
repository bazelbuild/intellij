package com.google.idea.sdkcompat.javascript;

import com.intellij.lang.typescript.tsconfig.TypeScriptConfig;
import com.intellij.lang.typescript.tsconfig.TypeScriptConfigService;
import com.intellij.openapi.project.Project;

import javax.annotation.Nonnull;
import java.util.List;

public abstract class DelegatingTypeScriptConfigServiceAdapter implements TypeScriptConfigService {
    protected TypeScriptConfigService impl;

    protected DelegatingTypeScriptConfigServiceAdapter(Project project) {
        this.impl = getConfigServiceInstance(project);
    }

    protected abstract TypeScriptConfigService getConfigServiceInstance(Project project);

    /** Removed in 2021.1. #api203 https://github.com/bazelbuild/intellij/issues/2329 */
    @Nonnull
    @Override
    public List<TypeScriptConfig> getConfigs() {
        return impl.getConfigs();
    }

    @Override
    public boolean hasConfigs() {
      return impl.hasConfigs();
    }
}
