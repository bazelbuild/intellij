package com.google.idea.sdkcompat.javascript;

import com.intellij.lang.typescript.tsconfig.TypeScriptConfigService;
import com.intellij.lang.typescript.tsconfig.TypeScriptConfigsChangedListener;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public abstract class TypeScriptConfigServiceAdapter implements TypeScriptConfigService {

    public abstract TypeScriptConfigService getImpl();

    @Override // deprecated, removed in 2023.1
    public List<VirtualFile> getConfigFiles() {
        return getImpl().getConfigFiles();
    }

    @Override // deprecated, removed in 2023.1
    public void addChangeListener(TypeScriptConfigsChangedListener listener) {
        getImpl().addChangeListener(listener);
    }

    @Override // Removed in 2023.1
    public ModificationTracker getConfigTracker(@Nullable VirtualFile file) {
        return getImpl().getConfigTracker(file);
    }

}
