package com.google.idea.sdkcompat.javascript;

import com.intellij.lang.typescript.tsconfig.TypeScriptConfig;
import com.intellij.lang.typescript.tsconfig.TypeScriptConfig.ModuleTarget;
import com.intellij.lang.typescript.tsconfig.TypeScriptFileImports;
import com.intellij.lang.typescript.tsconfig.TypeScriptFileImportsImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NotNullLazyValue;
import org.jetbrains.annotations.NotNull;

public abstract class TypeScriptConfigAdapter implements TypeScriptConfig {

    public abstract JSModuleTargetWrapper getAdapterModule();
    public abstract JSModuleResolutionWrapper getAdapterResolution();

    public abstract JSModuleResolutionWrapper getAdapterEffectiveResolution();
    @Override
    public @NotNull ModuleResolution getResolution() {
        return getAdapterResolution().value();
    }

    @Override
    public ModuleResolution getEffectiveResolution() {
        return getAdapterEffectiveResolution().value();
    }

    @Override
    public @NotNull ModuleTarget getModule() {
        return getAdapterModule().value();
    }

    NotNullLazyValue<TypeScriptFileImports> importStructure;

    //todo do it in constructor
    public void initImportsStructure(Project project) {
        this.importStructure =  NotNullLazyValue.createValue(() -> new TypeScriptFileImportsImpl(project, this));
    }

    @Override
    public TypeScriptFileImports getConfigImportResolveStructure() {
        return importStructure.getValue();
    }
}
