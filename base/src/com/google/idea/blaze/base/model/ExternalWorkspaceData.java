package com.google.idea.blaze.base.model;

import com.google.common.base.Functions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
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
    ImmutableList<ProjectData.ExternalWorkspace> protoWorkspaces = workspaces
        .values()
        .stream()
        .map(ExternalWorkspace::toProto)
        .collect(ImmutableList.toImmutableList());

    return ProjectData.ExternalWorkspaceData.newBuilder()
        .addAllWorkspaces(protoWorkspaces)
        .build();
  }

  public static ExternalWorkspaceData fromProto(ProjectData.ExternalWorkspaceData proto) {
    return new ExternalWorkspaceData(proto.getWorkspacesList().stream().map(ExternalWorkspace::fromProto).collect(ImmutableList.toImmutableList()));
  }

  @Nullable
  public ExternalWorkspace getByRepoName(String name) {
    return Maps.filterValues(workspaces, w -> w.repoName() != null && w.repoName().equals(name)).values().stream().findFirst().orElse(null);
  }
}
