package com.google.idea.blaze.base.model.primitives;

import com.google.devtools.intellij.model.ProjectData;

import javax.annotation.Nullable;
import com.google.auto.value.AutoValue;
import com.google.idea.blaze.base.ideinfo.ProtoWrapper;

@AutoValue
public abstract class ExternalWorkspace implements ProtoWrapper<ProjectData.ExternalWorkspace> {

    public abstract String name();

    @Nullable
    public abstract String repoName();

    public static ExternalWorkspace fromProto(ProjectData.ExternalWorkspace proto) {
        return create(proto.getName(), proto.getRepoName());
    }

    @Override
    public ProjectData.ExternalWorkspace toProto() {
        ProjectData.ExternalWorkspace.Builder builder = ProjectData.ExternalWorkspace.newBuilder().setName(name());
        if (repoName() != null && !repoName().isEmpty()) {
            builder = builder.setRepoName(repoName());
        }
        return builder.build();
    }

    public static ExternalWorkspace create(String name, String repoName) {
        ExternalWorkspace.Builder builder = ExternalWorkspace.builder().setName(name);
        if (repoName != null && !repoName.isEmpty()) {
            builder = builder.setRepoName(repoName);
        }
        return builder.build();
    }

    public static ExternalWorkspace.Builder builder() {
        return new AutoValue_ExternalWorkspace.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder setName(String name);
        public abstract Builder setRepoName(String repoName);
        public abstract ExternalWorkspace build();
    }

}
