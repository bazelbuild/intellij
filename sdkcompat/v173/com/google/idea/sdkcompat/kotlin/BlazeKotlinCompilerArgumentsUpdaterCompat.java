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
import org.jetbrains.kotlin.config.LanguageVersion;
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinCommonCompilerArgumentsHolder;

public final class BlazeKotlinCompilerArgumentsUpdaterCompat {
    private boolean updated = false;
    private final Project project;
    private final CommonCompilerArguments arguments;

    private BlazeKotlinCompilerArgumentsUpdaterCompat(Project project) {
        this.project=project;
        arguments = (CommonCompilerArguments) KotlinCommonCompilerArgumentsHolder.Companion.getInstance(project).getSettings().unfrozen();
    }

    public static BlazeKotlinCompilerArgumentsUpdaterCompat build(Project project) {
        return new BlazeKotlinCompilerArgumentsUpdaterCompat(project);
    }

    public String getApiVersion() {
        return this.arguments.getApiVersion();
    }

    public String getLanguageVersion() {
        return this.arguments.getLanguageVersion();
    }

    public void updateLanguageVersion(LanguageVersion languageVersion) {
        if ( getApiVersion() == null || !getApiVersion().equals(languageVersion.getVersionString())) {
            updated = true;
            arguments.setApiVersion(languageVersion.getVersionString());
        }
        if (getLanguageVersion() == null || !getLanguageVersion().equals(languageVersion.getVersionString())) {
            updated = true;
            arguments.setLanguageVersion(languageVersion.getVersionString());
        }
    }

    public void commit() {
        if (updated) {
            KotlinCommonCompilerArgumentsHolder.Companion.getInstance(project).setSettings(arguments);
        }
    }
}