/*
 * Copyright 2024 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.idea.sdkcompat.javascript;

import com.intellij.lang.javascript.frameworks.modules.JSModulePathMappings;
import com.intellij.lang.javascript.frameworks.modules.JSModulePathSubstitution;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.lang.javascript.config.JSFileImports;
import com.intellij.lang.javascript.config.JSFileImportsImpl;
import com.intellij.lang.javascript.config.JSModuleResolution;
import com.intellij.lang.javascript.config.JSModuleTarget;
import com.intellij.lang.typescript.tsconfig.TypeScriptConfig;
import org.jetbrains.annotations.NotNull;

public abstract class TypeScriptConfigAdapter implements TypeScriptConfig {

    public abstract JSModuleTargetWrapper getAdapterModule();

    public abstract JSModuleResolutionWrapper getAdapterResolution();

    public abstract JSModuleResolutionWrapper getAdapterEffectiveResolution();


    @Override
    public @NotNull JSModuleResolution getResolution() {
        return getAdapterResolution().value();
    }

    @Override
    public JSModuleResolution getEffectiveResolution() {
        return getAdapterEffectiveResolution().value();
    }

    @Override
    public @NotNull JSModuleTarget getModule() {
        return getAdapterModule().value();
    }
    NotNullLazyValue<JSFileImports> importStructure;

    //todo do it in constructor
    public void initImportsStructure(Project project) {
        this.importStructure =  NotNullLazyValue.createValue(() -> new JSFileImportsImpl(project, this));
    }

    @Override
    public JSFileImports getConfigImportResolveStructure() {
        return importStructure.getValue();
    }

    @Override
    public @NotNull JSModulePathMappings<JSModulePathSubstitution> getPathMappings() {
        return JSModulePathMappings.build(getPaths());
    }
}
