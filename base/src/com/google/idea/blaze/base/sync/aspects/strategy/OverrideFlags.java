package com.google.idea.blaze.base.sync.aspects.strategy;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

public  class OverrideFlags {
    @Contract(pure = true)
    static @NotNull String newRepositoryFlag(boolean useInjectedRepository) {
        if (useInjectedRepository) {
            return "--inject_repository";
        } else {
            return "--override_repository";
        }
    }

    public static @NotNull String overrideRepositoryFlag(boolean useInjectedRepository) {
        return String.format("%s=intellij_aspect", newRepositoryFlag(useInjectedRepository));
    }

    public static @NotNull String overrideRepositoryTemplateFlag(boolean useInjectedRepository) {
        return String.format("%s=intellij_aspect_template", newRepositoryFlag(useInjectedRepository));
    }
}
