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
