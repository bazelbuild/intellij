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

import com.google.idea.blaze.base.lang.buildfile.sync.BuildLanguageSpecService;
import com.google.idea.blaze.base.lang.buildfile.sync.LanguageSpecResult;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.qsync.QuerySync;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.intellij.openapi.project.Project;
import javax.annotation.Nullable;

/** Calls 'blaze info build-language', to retrieve the language spec. */
public class BuildLanguageSpecProviderImpl implements BuildLanguageSpecProvider {

  @Nullable
  @Override
  public BuildLanguageSpec getLanguageSpec(Project project) {
    if (QuerySync.isEnabled()) {
      BuildLanguageSpecService buildLanguageSpecService =
          project.getService(BuildLanguageSpecService.class);
      if (buildLanguageSpecService != null) {
        return buildLanguageSpecService.getLanguageSpec();
      } else {
        return null;
      }
    }
    BlazeProjectData blazeProjectData =
        BlazeProjectDataManager.getInstance(project).getBlazeProjectData();
    if (blazeProjectData == null) {
      return null;
    }
    LanguageSpecResult spec = blazeProjectData.getSyncState().get(LanguageSpecResult.class);
    if (spec == null) {
      return null;
    }
    return spec.getSpec();
  }
}
