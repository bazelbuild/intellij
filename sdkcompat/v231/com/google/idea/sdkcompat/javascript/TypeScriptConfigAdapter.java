package com.google.idea.sdkcompat.javascript;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.lang.javascript.config.JSFileImports;
import com.intellij.lang.javascript.config.JSFileImportsImpl;
import com.intellij.lang.javascript.config.JSModuleResolution;
import com.intellij.lang.javascript.config.JSModuleTarget;
import com.intellij.lang.typescript.tsconfig.TypeScriptConfig;
import org.jetbrains.annotations.NotNull;

// #api233
public abstract class TypeScriptConfigAdapter implements TypeScriptConfig {

    public abstract JSModuleTargetWrapper getAdapterModule();

    public abstract JSModuleResolutionWrapper getAdapterResolution();

    public abstract JSModuleResolutionWrapper getAdapterEffectiveResolution();


    @Override
    public @NotNull JSModuleResolution getResolution() {
        return getAdapterResolution().value();
    }

    @Override
    public JSModuleResolution getEffectiveResolution() {
        return getAdapterEffectiveResolution().value();
    }

    @Override
    public @NotNull JSModuleTarget getModule() {
        return getAdapterModule().value();
    }
    NotNullLazyValue<JSFileImports> importStructure;

    //todo do it in constructor
    public void initImportsStructure(Project project) {
        this.importStructure =  NotNullLazyValue.createValue(() -> new JSFileImportsImpl(project, this));
    }

    @Override
    public JSFileImports getConfigImportResolveStructure() {
        return importStructure.getValue();
    }
}
