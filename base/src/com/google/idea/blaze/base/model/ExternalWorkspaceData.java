/*
 * Copyright 2026 The Bazel Authors. All rights reserved.
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

package com.google.idea.blaze.base.model;

import com.google.common.base.Functions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.devtools.intellij.model.ProjectData;
import com.google.idea.blaze.base.ideinfo.ProtoWrapper;
import com.google.idea.blaze.base.model.primitives.ExternalWorkspace;

import javax.annotation.Nullable;

public final class ExternalWorkspaceData implements ProtoWrapper<ProjectData.ExternalWorkspaceData> {
  public ImmutableMap<String, ExternalWorkspace> workspaces;

  public static ExternalWorkspaceData EMPTY = new ExternalWorkspaceData(ImmutableList.of());

  public static ExternalWorkspaceData create(ImmutableList<ExternalWorkspace> workspaces) {
    return new ExternalWorkspaceData(workspaces);
  }

  ExternalWorkspaceData(ImmutableList<ExternalWorkspace> workspaces) {
    this.workspaces = ImmutableMap.copyOf(
        workspaces
            .stream()
            .collect(
                ImmutableMap.toImmutableMap(
                    ExternalWorkspace::repoName,
                    Functions.identity()))
    );
  }

  @Override
  public ProjectData.ExternalWorkspaceData toProto() {
    ProjectData.ExternalWorkspaceData.Builder builder =
        ProjectData.ExternalWorkspaceData.newBuilder();

    for (ExternalWorkspace externalWorkspace : workspaces.values()) {
      builder = builder.addWorkspaces(externalWorkspace.toProto());
    }

    return builder.build();
  }

  public static ExternalWorkspaceData fromProto(ProjectData.ExternalWorkspaceData proto) {
    return new ExternalWorkspaceData(
        proto.getWorkspacesList()
            .stream()
            .map(ExternalWorkspace::fromProto)
            .collect(ImmutableList.toImmutableList()));
  }

  @Nullable
  public ExternalWorkspace getByRepoName(String name) {
    return workspaces.get(name);
  }
}
