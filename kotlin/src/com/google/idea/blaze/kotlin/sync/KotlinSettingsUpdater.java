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
package com.google.idea.blaze.kotlin.sync;

import com.intellij.openapi.project.Project;
import org.jetbrains.kotlin.cli.common.arguments.CommonCompilerArguments;
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments;
import org.jetbrains.kotlin.idea.compiler.configuration.Kotlin2JvmCompilerArgumentsHolder;
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinCommonCompilerArgumentsHolder;

import java.util.function.Consumer;
import java.util.function.Supplier;

@SuppressWarnings("WeakerAccess")
final class KotlinSettingsUpdater {
  private boolean commonUpdated = false;
  private boolean jvmUpdated = false;
  private final Project project;
  private final CommonCompilerArguments commonArguments;
  private final K2JVMCompilerArguments jvmCompilerArguments;

  private KotlinSettingsUpdater(Project project) {
    this.project = project;
    commonArguments =
        (CommonCompilerArguments)
            KotlinCommonCompilerArgumentsHolder.Companion.getInstance(project)
                .getSettings()
                .unfrozen();
    jvmCompilerArguments =
        (K2JVMCompilerArguments)
            Kotlin2JvmCompilerArgumentsHolder.Companion.getInstance(project)
                .getSettings()
                .unfrozen();
  }

  public static KotlinSettingsUpdater create(Project project) {
    return new KotlinSettingsUpdater(project);
  }

  String apiVersion() {
    return this.commonArguments.getApiVersion();
  }

  public void apiVersion(String apiVersion) {
    commonSetting(apiVersion, this::apiVersion, commonArguments::setApiVersion);
  }

  String languageVersion() {
    return this.commonArguments.getLanguageVersion();
  }

  public void languageVersion(String languageVersion) {
    commonSetting(languageVersion, this::languageVersion, commonArguments::setLanguageVersion);
  }

  String coroutineState() {
    return this.commonArguments.getCoroutinesState();
  }

  public void coroutineState(String coroutines) {
    commonSetting(coroutines, this::coroutineState, commonArguments::setCoroutinesState);
  }

  String jvmTarget() {
    return this.jvmCompilerArguments.getJvmTarget();
  }

  public void jvmTarget(String jvmTarget) {
    jvmSetting(jvmTarget, this::jvmTarget, jvmCompilerArguments::setJvmTarget);
  }

  private <T> void commonSetting(T value, Supplier<T> settingProvider, Consumer<T> updater) {
    T current = settingProvider.get();
    if(current == null || !current.equals(value)) {
      commonUpdated = true;
      updater.accept(value);
    }
  }

  private <T> void jvmSetting(T value, Supplier<T> settingProvider, Consumer<T> updater) {
    T current = settingProvider.get();
    if(current == null || !current.equals(value)) {
      jvmUpdated = true;
      updater.accept(value);
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
