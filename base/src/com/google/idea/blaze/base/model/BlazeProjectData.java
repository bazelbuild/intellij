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

import com.google.idea.blaze.base.command.info.BlazeInfo;
import com.google.idea.blaze.base.ideinfo.TargetMap;
import com.google.idea.blaze.base.sync.projectview.WorkspaceLanguageSettings;
import com.google.idea.blaze.base.sync.workspace.ArtifactLocationDecoder;
import com.google.idea.blaze.base.sync.workspace.WorkspacePathResolver;
import java.io.Serializable;
import javax.annotation.concurrent.Immutable;

/** The top-level object serialized to cache. */
@Immutable
public class BlazeProjectData implements Serializable {
  private static final long serialVersionUID = 28L;

  private final long syncTime;
  private final TargetMap targetMap;
  private final BlazeInfo blazeInfo;
  private final BlazeVersionData blazeVersionData;
  private final WorkspacePathResolver workspacePathResolver;
  private final ArtifactLocationDecoder artifactLocationDecoder;
  private final WorkspaceLanguageSettings workspaceLanguageSettings;
  private final SyncState syncState;

  public BlazeProjectData(
      long syncTime,
      TargetMap targetMap,
      BlazeInfo blazeInfo,
      BlazeVersionData blazeVersionData,
      WorkspacePathResolver workspacePathResolver,
      ArtifactLocationDecoder artifactLocationDecoder,
      WorkspaceLanguageSettings workspaceLanguageSettings,
      SyncState syncState) {
    this.syncTime = syncTime;
    this.targetMap = targetMap;
    this.blazeInfo = blazeInfo;
    this.blazeVersionData = blazeVersionData;
    this.workspacePathResolver = workspacePathResolver;
    this.artifactLocationDecoder = artifactLocationDecoder;
    this.workspaceLanguageSettings = workspaceLanguageSettings;
    this.syncState = syncState;
  }

  public long getSyncTime() {
    return syncTime;
  }

  public TargetMap getTargetMap() {
    return targetMap;
  }

  public BlazeInfo getBlazeInfo() {
    return blazeInfo;
  }

  public BlazeVersionData getBlazeVersionData() {
    return blazeVersionData;
  }

  public WorkspacePathResolver getWorkspacePathResolver() {
    return workspacePathResolver;
  }

  public ArtifactLocationDecoder getArtifactLocationDecoder() {
    return artifactLocationDecoder;
  }

  public WorkspaceLanguageSettings getWorkspaceLanguageSettings() {
    return workspaceLanguageSettings;
  }

  public SyncState getSyncState() {
    return syncState;
  }
}
