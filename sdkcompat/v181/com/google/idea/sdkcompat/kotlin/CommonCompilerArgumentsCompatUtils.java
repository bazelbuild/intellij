package com.google.idea.sdkcompat.kotlin;

import com.intellij.openapi.project.Project;
import org.jetbrains.kotlin.cli.common.arguments.CommonCompilerArguments;
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinCommonCompilerArgumentsHolder;

/** SDK adapter for getting and setting {@link CommonCompilerArguments}. */
public final class CommonCompilerArgumentsCompatUtils {
  public static CommonCompilerArguments getUnfrozenSettings(Project project) {
    return (CommonCompilerArguments)
        KotlinCommonCompilerArgumentsHolder.Companion.getInstance(project).getSettings().unfrozen();
  }

  public static String getApiVersion(CommonCompilerArguments settings) {
    return settings.getApiVersion();
  }

  public static void setApiVersion(CommonCompilerArguments settings, String apiVersion) {
    settings.setApiVersion(apiVersion);
  }

  public static String getLanguageVersion(CommonCompilerArguments settings) {
    return settings.getLanguageVersion();
  }

  public static void setLanguageVersion(CommonCompilerArguments settings, String languageVersion) {
    settings.setLanguageVersion(languageVersion);
  }
}
