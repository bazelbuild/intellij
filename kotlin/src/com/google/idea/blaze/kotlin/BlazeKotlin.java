package com.google.idea.blaze.kotlin;

import org.jetbrains.kotlin.config.LanguageVersion;
import org.jetbrains.kotlin.idea.KotlinPluginUtil;

import java.util.function.Function;

/** Contains constants for the Kotlin plugin. */
public final class BlazeKotlin {
  /** The language level to use if the user has not provided one in the workspace. */
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
            "The Kotlin workspace has not been setup. Visit "
                + RULES_REPO
                + " , or remove the Kotlin language.",
        MULTIPLE_KT_TOOLCHAIN_IDE_INFO = "Multiple kt_toolchain_ide_info rules in repo",
        LANGUAGE_VERSION_SECTION_IGNORED =
            "The Kotlin rules configure all of the project settings, \"kotlin_language_version\" is ignored";
    public static final Function<String, String>
        UPDATE_RULES_WARNING =
            (because) -> because + ". Please update the rules by visiting " + RULES_REPO + ".",
        ERROR_RENDERING_KT_TOOLCHAIN_IDE_INFO =
            (because) -> "Could not render ide info json file, error: " + because;
  }
}
