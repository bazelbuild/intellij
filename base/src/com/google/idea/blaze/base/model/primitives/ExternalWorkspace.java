package com.google.idea.blaze.base.model.primitives;

import com.google.devtools.intellij.model.ProjectData;

import javax.annotation.Nullable;

import com.google.auto.value.AutoValue;
import com.google.idea.blaze.base.ideinfo.ProtoWrapper;

@AutoValue
public abstract class ExternalWorkspace implements ProtoWrapper<ProjectData.ExternalWorkspace> {

  public abstract String name();

  public abstract String repoName();

  public static ExternalWorkspace fromProto(ProjectData.ExternalWorkspace proto) {
    return create(proto.getName(), proto.getRepoName());
  }

  @Override
  public ProjectData.ExternalWorkspace toProto() {
    return ProjectData.ExternalWorkspace.newBuilder()
               .setName(name())
               .setRepoName(repoName())
               .build();
  }

  public static ExternalWorkspace create(String name, String repoName) {
    return ExternalWorkspace.builder()
               .setName(name)
               .setRepoName(repoName)
               .build();
  }

  public static ExternalWorkspace.Builder builder() {
    return new AutoValue_ExternalWorkspace.Builder();
  }

  @AutoValue.Builder
  public abstract static class Builder {
    abstract Builder setName(String name);

    abstract Builder setRepoName(String repoName);

    abstract ExternalWorkspace build();
  }
}