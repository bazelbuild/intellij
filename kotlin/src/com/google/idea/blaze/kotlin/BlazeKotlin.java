package com.google.idea.blaze.kotlin;

import org.jetbrains.kotlin.config.LanguageVersion;
import org.jetbrains.kotlin.idea.KotlinPluginUtil;

/**
 * Contains constants for the Kotlin plugin.
 */
public final class BlazeKotlin {
    /**
     * The language level to use if the user has not provided one in the workspace.
     */
    public static final LanguageVersion DEFAULT_LANGUAGE_VERSION = LanguageVersion.LATEST_STABLE;

    public static final String
            /*
              The name of the workspace in which to find the Kotlin compiler. This is setup by the rules.
             */
            COMPILER_WORKSPACE_NAME = "com_github_jetbrains_kotlin",
            KOTLIN_RULES_WORKSPACE_NAME = "io_bazel_rules_kotlin",
            PLUGIN_ID = KotlinPluginUtil.KOTLIN_PLUGIN_ID.getIdString(),
            RULES_REPO = "https://github.com/bazelbuild/rules_kotlin";

    public static class Issues {
        public static final String
                RULES_ABSENT_FROM_WORKSPACE =
                "The Kotlin workspace has not been setup. Visit " + RULES_REPO + " , or remove the Kotlin language.";
    }
}
