package com.google.idea.sdkcompat.golang;

import com.goide.execution.application.GoApplicationConfiguration;
import com.goide.execution.application.GoApplicationConfiguration.Kind;

/** Provides SDK compatibility shims for golang plugin API classes. */
public class BlazeGoRunConfigurationRunnerCompat {
    private BlazeGoRunConfigurationRunnerCompat() {}

    public abstract static class BlazeGoRunConfigurationRunnerAdapter {

        public static void setNativeConfigKindToPackage(GoApplicationConfiguration nativeConfig) {
            nativeConfig.setKind(Kind.PACKAGE);
        }
    }
}
