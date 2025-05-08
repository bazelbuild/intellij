/*
 * Copyright 2025 The Bazel Authors. All rights reserved.
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

package com.google.idea.blaze.base.sync;

import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.settings.BlazeImportSettings;
import com.google.idea.blaze.base.targetmaps.SourceToTargetMap;
import com.intellij.openapi.project.Project;
import java.io.File;

public class SourceToTargetMapInitializer implements SyncListener {

  @Override
  public void onSyncComplete(Project project, BlazeContext context,
      BlazeImportSettings importSettings, ProjectViewSet projectViewSet,
      ImmutableSet<Integer> buildIds, BlazeProjectData blazeProjectData, SyncMode syncMode,
      SyncResult syncResult) {
    // eagerly instantiate source to target map
    SourceToTargetMap.getInstance(project).getRulesForSourceFile(new File(""));
  }
}
