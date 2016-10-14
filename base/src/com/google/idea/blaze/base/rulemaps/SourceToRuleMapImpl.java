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
import com.google.idea.blaze.base.ideinfo.RuleKey;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.settings.BlazeImportSettings;
import com.google.idea.blaze.base.sync.SyncListener;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.base.sync.workspace.ArtifactLocationDecoder;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import java.io.File;
import java.util.Objects;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

/** Maps source files to their respective targets */
public class SourceToRuleMapImpl implements SourceToRuleMap {
  private final Project project;
  private ImmutableMultimap<File, RuleKey> sourceToTargetMap;

  public static SourceToRuleMapImpl getImpl(Project project) {
    return (SourceToRuleMapImpl) ServiceManager.getService(project, SourceToRuleMap.class);
  }

  public SourceToRuleMapImpl(Project project) {
    this.project = project;
  }

  @Override
  public ImmutableCollection<Label> getTargetsToBuildForSourceFile(File sourceFile) {
    BlazeProjectData blazeProjectData =
        BlazeProjectDataManager.getInstance(project).getBlazeProjectData();
    if (blazeProjectData == null) {
      return ImmutableList.of();
    }
    return ImmutableList.copyOf(
        getRulesForSourceFile(sourceFile)
            .stream()
            .map(blazeProjectData.ruleMap::get)
            .filter(Objects::nonNull)
            // TODO(tomlu): For non-plain targets we need to rdep our way back to a target to build
            // Without this, you won't be able to invoke "build" on (say) a proto_library
            .filter(RuleIdeInfo::isPlainTarget)
            .map(rule -> rule.label)
            .collect(Collectors.toList()));
  }

  @Override
  public ImmutableCollection<RuleKey> getRulesForSourceFile(File sourceFile) {
    ImmutableMultimap<File, RuleKey> sourceToTargetMap = getSourceToTargetMap();
    if (sourceToTargetMap == null) {
      return ImmutableList.of();
    }
    return sourceToTargetMap.get(sourceFile);
  }

  @Nullable
  private synchronized ImmutableMultimap<File, RuleKey> getSourceToTargetMap() {
    if (this.sourceToTargetMap == null) {
      this.sourceToTargetMap = initSourceToTargetMap();
    }
    return this.sourceToTargetMap;
  }

  private synchronized void clearSourceToTargetMap() {
    this.sourceToTargetMap = null;
  }

  @Nullable
  private ImmutableMultimap<File, RuleKey> initSourceToTargetMap() {
    BlazeProjectData blazeProjectData =
        BlazeProjectDataManager.getInstance(project).getBlazeProjectData();
    if (blazeProjectData == null) {
      return null;
    }
    ArtifactLocationDecoder artifactLocationDecoder = blazeProjectData.artifactLocationDecoder;
    ImmutableMultimap.Builder<File, RuleKey> sourceToTargetMap = ImmutableMultimap.builder();
    for (RuleIdeInfo rule : blazeProjectData.ruleMap.rules()) {
      RuleKey key = rule.key;
      for (ArtifactLocation sourceArtifact : rule.sources) {
        sourceToTargetMap.put(artifactLocationDecoder.decode(sourceArtifact), key);
      }
    }
    return sourceToTargetMap.build();
  }

  static class ClearSourceToTargetMap extends SyncListener.Adapter {
    @Override
    public void onSyncComplete(
        Project project,
        BlazeContext context,
        BlazeImportSettings importSettings,
        ProjectViewSet projectViewSet,
        BlazeProjectData blazeProjectData,
        SyncResult syncResult) {
      getImpl(project).clearSourceToTargetMap();
    }
  }
}
