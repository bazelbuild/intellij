package com.google.idea.sdkcompat.javascript;

import com.intellij.lang.typescript.tsconfig.TypeScriptConfig.ModuleResolution;

public enum JSModuleResolutionWrapper {
    NODE(ModuleResolution.NODE),
    NODENEXT(ModuleResolution.NODENEXT),
    CLASSIC(ModuleResolution.CLASSIC),
    UNKNOWN(ModuleResolution.UNKNOWN);

    private final ModuleResolution value;

    JSModuleResolutionWrapper(ModuleResolution value){
        this.value = value;
    }

    public ModuleResolution value() {
        return value;
    }
}
