package com.google.idea.sdkcompat.javascript;

import com.intellij.lang.typescript.tsconfig.TypeScriptConfig;
import com.intellij.lang.typescript.tsconfig.TypeScriptConfigService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

public abstract class DelegatingTypeScriptConfigServiceAdapter implements TypeScriptConfigService {
    protected TypeScriptConfigService impl;

    protected DelegatingTypeScriptConfigServiceAdapter(Project project) {
        this.impl = getConfigServiceInstance(project);
    }

    protected abstract TypeScriptConfigService getConfigServiceInstance(Project project);

    /** #api203 new API in 2021.1 */
    @Override
    public TypeScriptConfig getPreferableConfig(@Nonnull VirtualFile scopeFile) {
        return impl.getPreferableConfig(scopeFile);
    }

    /** #api203 new API in 2021.1 */
    @Override
    public @Nullable
    TypeScriptConfig getPreferableOrParentConfig(@Nullable final VirtualFile virtualFile) {
      return impl.getPreferableOrParentConfig(virtualFile);
    }

    /** #api203 new API in 2021.1 */
    @Override
    public @Nullable TypeScriptConfig getDirectIncludePreferableConfig(@Nullable final VirtualFile virtualFile) {
      return impl.getDirectIncludePreferableConfig(virtualFile);
    }

    /** #api203 new API in 2021.1 */
    @Override
    public List<VirtualFile> getRootConfigFiles() {
      return impl.getRootConfigFiles();
    }
}
