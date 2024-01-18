package com.google.idea.sdkcompat.javascript;

import com.intellij.lang.javascript.config.JSModuleResolution;

public enum JSModuleResolutionWrapper {
    NODE(JSModuleResolution.NODE),
    NODENEXT(JSModuleResolution.NODENEXT),
    CLASSIC(JSModuleResolution.CLASSIC),
    UNKNOWN(JSModuleResolution.UNKNOWN);

    private final JSModuleResolution value;

    JSModuleResolutionWrapper(JSModuleResolution value){
        this.value = value;
    }

    public JSModuleResolution value() {
        return value;
    }
}
