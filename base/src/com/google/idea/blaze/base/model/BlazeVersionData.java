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

import com.google.idea.blaze.base.bazel.BazelVersion;
import com.google.idea.blaze.base.bazel.BuildSystemProvider;
import com.google.idea.blaze.base.command.info.BlazeInfo;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.settings.BuildSystem;
import java.io.Serializable;
import javax.annotation.Nullable;

/**
 * Version data about the user's blaze/bazel and other info needed for switching behaviour
 * dynamically.
 */
public class BlazeVersionData implements Serializable {
  private static final long serialVersionUID = 2L;

  @Nullable private final Long blazeCl;
  @Nullable private final Long clientCl;
  @Nullable private final BazelVersion bazelVersion;

  private BlazeVersionData(
      @Nullable Long blazeCl, @Nullable Long clientCl, @Nullable BazelVersion bazelVersion) {
    this.blazeCl = blazeCl;
    this.clientCl = clientCl;
    this.bazelVersion = bazelVersion;
  }

  public boolean blazeVersionIsKnown() {
    return blazeCl != null;
  }

  public boolean blazeContainsCl(long cl) {
    return blazeCl != null && blazeCl >= cl;
  }

  public boolean blazeClientIsKnown() {
    return clientCl != null;
  }

  public boolean blazeClientIsAtLeastCl(long cl) {
    return clientCl != null && clientCl >= cl;
  }

  public boolean bazelIsAtLeastVersion(int major, int minor, int bugfix) {
    return bazelVersion != null && bazelVersion.isAtLeast(major, minor, bugfix);
  }

  public boolean bazelIsAtLeastVersion(BazelVersion version) {
    return bazelVersion != null && bazelVersion.isAtLeast(version);
  }

  public BuildSystem buildSystem() {
    return bazelVersion != null ? BuildSystem.Bazel : BuildSystem.Blaze;
  }

  @Override
  public String toString() {
    if (bazelVersion != null) {
      return bazelVersion.toString();
    }
    return String.format("Blaze CL: %s, Client CL: %s", blazeCl, clientCl);
  }

  public static BlazeVersionData build(
      BuildSystem buildSystem, WorkspaceRoot workspaceRoot, BlazeInfo blazeInfo) {
    Builder builder = builder();
    for (BuildSystemProvider provider : BuildSystemProvider.EP_NAME.getExtensions()) {
      provider.populateBlazeVersionData(buildSystem, workspaceRoot, blazeInfo, builder);
    }
    return builder.build();
  }

  public static Builder builder() {
    return new Builder();
  }

  /** Builder class for constructing the blaze version data */
  public static class Builder {
    private Long blazeCl;
    private Long clientCl;
    private BazelVersion bazelVersion;

    public Builder setBlazeCl(Long blazeCl) {
      this.blazeCl = blazeCl;
      return this;
    }

    public Builder setClientCl(Long clientCl) {
      this.clientCl = clientCl;
      return this;
    }

    public Builder setBazelVersion(BazelVersion bazelVersion) {
      this.bazelVersion = bazelVersion;
      return this;
    }

    public BlazeVersionData build() {
      return new BlazeVersionData(blazeCl, clientCl, bazelVersion);
    }
  }
}
