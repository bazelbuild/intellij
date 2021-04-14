package com.google.idea.sdkcompat.javascript;

import com.google.common.collect.ImmutableMap;
import com.intellij.lang.typescript.tsconfig.TypeScriptConfig;
import com.intellij.lang.typescript.tsconfig.TypeScriptConfigService;
import com.intellij.lang.typescript.tsconfig.TypeScriptConfigUtil;
import com.intellij.openapi.vfs.VirtualFile;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

public abstract class BlazeTypeScriptConfigServiceAdapter implements TypeScriptConfigService {
    protected volatile ImmutableMap<VirtualFile, TypeScriptConfig> configs;

    @Override
    public TypeScriptConfig getPreferableConfig(@Nonnull VirtualFile scopeFile) {
        return configs.get(
                TypeScriptConfigUtil.getNearestParentConfigFile(scopeFile, configs.keySet()));
    }

    @Nullable
    @Override
    public TypeScriptConfig parseConfigFile(VirtualFile file) {
      return null;
    }

    /** Removed in 2021.1. #api203 https://github.com/bazelbuild/intellij/issues/2329 */
    @Nonnull
    @Override
    public List<TypeScriptConfig> getConfigs() {
        return getTypeScriptConfigs();
    }

    public List<TypeScriptConfig> getTypeScriptConfigs() {
      return configs.values().asList();
    }

    @Override
    public boolean hasConfigs() {
        return !configs.isEmpty();
    }
}
