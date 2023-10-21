package com.google.idea.sdkcompat.javascript;

import com.intellij.lang.javascript.config.JSModuleTarget;

public enum JSModuleTargetWrapper {
    COMMON_JS(JSModuleTarget.COMMON_JS),
    OTHER(JSModuleTarget.OTHER),
    NODENEXT(JSModuleTarget.NODENEXT),
    UNKNOWN(JSModuleTarget.UNKNOWN);

    private final JSModuleTarget value;

    public JSModuleTarget value() {
        return value;
    }

    JSModuleTargetWrapper(JSModuleTarget value) {
        this.value = value;
    }
}
