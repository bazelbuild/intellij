/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.lang.buildfile.language.semantics;

import com.google.common.collect.Maps;
import com.google.idea.blaze.base.lang.buildfile.sync.LanguageSpecResult;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.settings.BlazeImportSettings;
import com.google.idea.blaze.base.sync.SyncListener;
import com.intellij.openapi.project.Project;

import java.util.Map;

/**
 * Calls 'blaze info build-language', to retrieve the language spec.
 */
public class BuildLanguageSpecProviderImpl extends SyncListener.Adapter implements BuildLanguageSpecProvider {

  private static final Map<Project, LanguageSpecResult> calculatedSpecs = Maps.newHashMap();

  @Override
  public BuildLanguageSpec getLanguageSpec(Project project) {
    LanguageSpecResult result = calculatedSpecs.get(project);
    return result != null ? result.spec : null;
  }

  @Override
  public void onSyncComplete(Project project,
                             BlazeImportSettings importSettings,
                             ProjectViewSet projectViewSet,
                             BlazeProjectData blazeProjectData) {
    LanguageSpecResult spec = blazeProjectData.syncState.get(LanguageSpecResult.class);
    if (spec != null) {
      calculatedSpecs.put(project, spec);
    }
  }

}
