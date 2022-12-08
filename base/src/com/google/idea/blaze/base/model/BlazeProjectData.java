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

/** Interface to persistent project data. */
public interface BlazeProjectData {

  // TODO: Many of the methods herein are aspect-sync specific, and should probably not appear in
  //  this interface.

  ProjectTargetData getTargetData();

  TargetMap getTargetMap();

  BlazeInfo getBlazeInfo();

  BlazeVersionData getBlazeVersionData();

  WorkspacePathResolver getWorkspacePathResolver();

  ArtifactLocationDecoder getArtifactLocationDecoder();

  WorkspaceLanguageSettings getWorkspaceLanguageSettings();

  RemoteOutputArtifacts getRemoteOutputs();

  SyncState getSyncState();
}
