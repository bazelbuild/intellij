/*
 * Copyright 2018 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
