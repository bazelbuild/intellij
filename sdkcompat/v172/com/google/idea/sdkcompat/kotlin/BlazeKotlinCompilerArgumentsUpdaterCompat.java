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
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments;
import org.jetbrains.kotlin.idea.compiler.configuration.Kotlin2JvmCompilerArgumentsHolder;
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinCommonCompilerArgumentsHolder;

public final class BlazeKotlinCompilerArgumentsUpdaterCompat {
  private boolean commonUpdated = false;
  private boolean jvmUpdated = false;
  private final Project project;
  private final CommonCompilerArguments commonArguments;
  private final K2JVMCompilerArguments jvmCompilerArguments;

  private BlazeKotlinCompilerArgumentsUpdaterCompat(Project project) {
    this.project = project;
    commonArguments =
        KotlinCommonCompilerArgumentsHolder.Companion.getInstance(project).getSettings();
    jvmCompilerArguments =
        Kotlin2JvmCompilerArgumentsHolder.Companion.getInstance(project).getSettings();
  }

  public static BlazeKotlinCompilerArgumentsUpdaterCompat build(Project project) {
    return new BlazeKotlinCompilerArgumentsUpdaterCompat(project);
  }

  public String getApiVersion() {
    return this.commonArguments.apiVersion;
  }

  public String getLanguageVersion() {
    return this.commonArguments.languageVersion;
  }

  public String getCoroutineState() {
    return this.commonArguments.coroutinesState;
  }

  public String getJvmTarget() {
    return this.jvmCompilerArguments.jvmTarget;
  }

  public void updateApiVersion(String apiVersion) {
    if (getApiVersion() == null || !getApiVersion().equals(apiVersion)) {
      commonUpdated = true;
      commonArguments.apiVersion = apiVersion;
    }
  }

  public void updateLanguageVersion(String languageVersion) {
    if (getLanguageVersion() == null || !getLanguageVersion().equals(languageVersion)) {
      commonUpdated = true;
      commonArguments.languageVersion = languageVersion;
    }
  }

  public void updateCoroutineState(String coroutines) {
    if (getCoroutineState() == null || !getCoroutineState().equals(coroutines)) {
      commonUpdated = true;
      commonArguments.coroutinesState = coroutines;
    }
  }

  public void updateJvmTarget(String jvmTarget) {
    if (getJvmTarget() == null || !getJvmTarget().equals(jvmTarget)) {
      jvmUpdated = true;
      jvmCompilerArguments.jvmTarget = jvmTarget;
    }
  }

  public void commit() {
    if (commonUpdated) {
      KotlinCommonCompilerArgumentsHolder.Companion.getInstance(project)
          .setSettings(commonArguments);
    }
    if (jvmUpdated) {
      Kotlin2JvmCompilerArgumentsHolder.Companion.getInstance(project)
          .setSettings(jvmCompilerArguments);
    }
  }
}
