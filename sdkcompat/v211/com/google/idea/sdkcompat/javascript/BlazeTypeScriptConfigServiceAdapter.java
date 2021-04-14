package com.google.idea.sdkcompat.javascript;

import com.google.common.collect.ImmutableMap;
import com.intellij.lang.typescript.tsconfig.TypeScriptConfig;
import com.intellij.lang.typescript.tsconfig.TypeScriptConfigService;
import com.intellij.openapi.vfs.VirtualFile;

import javax.annotation.Nullable;
import java.util.List;

public abstract class BlazeTypeScriptConfigServiceAdapter implements TypeScriptConfigService {
    protected volatile ImmutableMap<VirtualFile, TypeScriptConfig> configs;

    @Nullable
    @Override
    public TypeScriptConfig getPreferableConfig(VirtualFile scopeFile) {
        throw new RuntimeException("TypeScript support not implemented");
    }

    @Override
    public TypeScriptConfig getPreferableOrParentConfig(final VirtualFile scopeFile) {
      // TODO - TypeScript is currently unsupported by this version.
      throw new RuntimeException("TypeScript support not implemented");
    }

    @Override
    public TypeScriptConfig getDirectIncludePreferableConfig(final VirtualFile scopeFile) {
      // TODO - TypeScript is currently unsupported by this version.
      throw new RuntimeException("TypeScript support not implemented");
    }

    @Override
    public List<VirtualFile> getRootConfigFiles() {
      // TODO - TypeScript is currently unsupported by this version.
      throw new RuntimeException("TypeScript support not implemented");
    }

    public List<TypeScriptConfig> getTypeScriptConfigs() {
      return configs.values().asList();
    }
}
