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
package com.google.idea.blaze.base.rulemaps;

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMultimap;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.ideinfo.RuleIdeInfo;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.settings.BlazeImportSettings;
import com.google.idea.blaze.base.sync.SyncListener;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;

import javax.annotation.Nullable;
import java.io.File;

/**
 * Maps source files to their respective targets
 */
public class SourceToRuleMapImpl implements SourceToRuleMap {
  private final Project project;
  private ImmutableMultimap<File, Label> sourceToTargetMap;

  public static SourceToRuleMapImpl getImpl(Project project) {
    return (SourceToRuleMapImpl) ServiceManager.getService(project, SourceToRuleMap.class);
  }

  public SourceToRuleMapImpl(Project project) {
    this.project = project;
  }

  @Override
  public ImmutableCollection<Label> getTargetsForSourceFile(File file) {
    ImmutableMultimap<File, Label> sourceToTargetMap = getSourceToTargetMap();
    return sourceToTargetMap != null ? sourceToTargetMap.get(file) : ImmutableList.of();
  }

  @Nullable
  private synchronized ImmutableMultimap<File, Label> getSourceToTargetMap() {
    if (this.sourceToTargetMap == null) {
      this.sourceToTargetMap = initSourceToTargetMap();
    }
    return this.sourceToTargetMap;
  }

  private synchronized void clearSourceToTargetMap() {
    this.sourceToTargetMap = null;
  }

  @Nullable
  private ImmutableMultimap<File, Label> initSourceToTargetMap() {
    BlazeProjectData blazeProjectData = BlazeProjectDataManager.getInstance(project).getBlazeProjectData();
    if (blazeProjectData == null) {
      return null;
    }
    ImmutableMultimap.Builder<File, Label> sourceToTargetMap = ImmutableMultimap.builder();
    for (RuleIdeInfo rule : blazeProjectData.ruleMap.values()) {
      Label label = rule.label;
      for (ArtifactLocation sourceArtifact : rule.sources) {
        sourceToTargetMap.put(sourceArtifact.getFile(), label);
      }
    }
    return sourceToTargetMap.build();
  }

  static class ClearSourceToTargetMap extends SyncListener.Adapter {
    @Override
    public void onSyncComplete(Project project,
                               BlazeImportSettings importSettings,
                               ProjectViewSet projectViewSet,
                               BlazeProjectData blazeProjectData) {
      getImpl(project).clearSourceToTargetMap();
    }
  }
}
