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
package com.google.idea.blaze.base.model;

import com.google.common.collect.ImmutableMultimap;
import com.google.idea.blaze.base.ideinfo.RuleKey;
import com.google.idea.blaze.base.ideinfo.RuleMap;
import com.google.idea.blaze.base.sync.projectview.WorkspaceLanguageSettings;
import com.google.idea.blaze.base.sync.workspace.ArtifactLocationDecoder;
import com.google.idea.blaze.base.sync.workspace.BlazeRoots;
import com.google.idea.blaze.base.sync.workspace.WorkingSet;
import com.google.idea.blaze.base.sync.workspace.WorkspacePathResolver;
import java.io.Serializable;
import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

/** The top-level object serialized to cache. */
@Immutable
public class BlazeProjectData implements Serializable {
  private static final long serialVersionUID = 23L;

  public final long syncTime;
  public final RuleMap ruleMap;
  public final BlazeRoots blazeRoots;
  @Nullable public final WorkingSet workingSet;
  public final WorkspacePathResolver workspacePathResolver;
  public final ArtifactLocationDecoder artifactLocationDecoder;
  public final WorkspaceLanguageSettings workspaceLanguageSettings;
  public final SyncState syncState;
  public final ImmutableMultimap<RuleKey, RuleKey> reverseDependencies;
  @Nullable public final String vcsName;

  public BlazeProjectData(
      long syncTime,
      RuleMap ruleMap,
      BlazeRoots blazeRoots,
      @Nullable WorkingSet workingSet,
      WorkspacePathResolver workspacePathResolver,
      ArtifactLocationDecoder artifactLocationDecoder,
      WorkspaceLanguageSettings workspaceLangaugeSettings,
      SyncState syncState,
      ImmutableMultimap<RuleKey, RuleKey> reverseDependencies,
      String vcsName) {
    this.syncTime = syncTime;
    this.ruleMap = ruleMap;
    this.blazeRoots = blazeRoots;
    this.workingSet = workingSet;
    this.workspacePathResolver = workspacePathResolver;
    this.artifactLocationDecoder = artifactLocationDecoder;
    this.workspaceLanguageSettings = workspaceLangaugeSettings;
    this.syncState = syncState;
    this.reverseDependencies = reverseDependencies;
    this.vcsName = vcsName;
  }
}
