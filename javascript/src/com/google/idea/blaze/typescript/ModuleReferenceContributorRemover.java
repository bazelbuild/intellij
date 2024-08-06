/*
 * Copyright 2021 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.typescript;

import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.common.experiments.BoolExperiment;
import com.intellij.lang.javascript.frameworks.modules.JSDefaultModuleReferenceContributor;
import com.intellij.lang.javascript.psi.resolve.JSModuleReferenceContributor;
import com.intellij.lang.typescript.modules.TypeScriptExternalModuleReferenceContributor;
import com.intellij.lang.typescript.modules.TypeScriptModuleReferenceContributor;
import com.intellij.lang.typescript.modules.TypeScriptNodeModulesReferenceContributor;
import com.intellij.lang.typescript.modules.TypeScriptPathMappingReferenceContributor;
import com.intellij.openapi.extensions.ExtensionPoint;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import org.jetbrains.annotations.NotNull;

/**
 * Replaces several {@link JSModuleReferenceContributor} at runtime to avoid b/197086700 which is
 * caused by the file watching of {@code /google/src/cloud/node_modules}.
 *
 * <p>TODO(b/197086700): Find a permanent fix for excessive file watching
 */
public class ModuleReferenceContributorRemover implements StartupActivity.DumbAware {
  private static final BoolExperiment enable =
      new BoolExperiment("remove.typescript.reference.contributors", true);

  @Override
  public void runActivity(@NotNull Project project) {
    if (!enable.getValue()) {
      return;
    }
    if (!isBlazeTypeScriptProject(project)) {
      return;
    }
    ExtensionPoint<JSModuleReferenceContributor> ep =
        JSModuleReferenceContributor.EP_NAME.getPoint();
    ep.unregisterExtension(JSDefaultModuleReferenceContributor.class);
    ep.unregisterExtension(TypeScriptModuleReferenceContributor.class);
    ep.unregisterExtension(TypeScriptNodeModulesReferenceContributor.class);
    ep.unregisterExtension(TypeScriptPathMappingReferenceContributor.class);
    ep.unregisterExtension(TypeScriptExternalModuleReferenceContributor.class);
  }

  private boolean isBlazeTypeScriptProject(@NotNull Project project) {
    if (!Blaze.isBlazeProject(project)) {
      return false;
    }
    BlazeProjectData blazeProjectData =
        BlazeProjectDataManager.getInstance(project).getBlazeProjectData();
    return blazeProjectData != null
        && blazeProjectData
            .getWorkspaceLanguageSettings()
            .getActiveLanguages()
            .contains(LanguageClass.TYPESCRIPT);
  }
}
