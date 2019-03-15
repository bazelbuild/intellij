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
package com.google.idea.blaze.base.ideinfo;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.devtools.intellij.ideinfo.IntellijIdeInfo;
import com.google.devtools.intellij.ideinfo.IntellijIdeInfo.PyIdeInfo.PythonVersion;
import com.google.idea.blaze.base.model.primitives.Label;
import java.util.Objects;
import javax.annotation.Nullable;

/** Ide info specific to python rules. */
public final class PyIdeInfo implements ProtoWrapper<IntellijIdeInfo.PyIdeInfo> {
  private final ImmutableList<ArtifactLocation> sources;
  @Nullable private final Label launcher;
  private final PythonVersion version;

  private PyIdeInfo(
      ImmutableList<ArtifactLocation> sources, @Nullable Label launcher, PythonVersion version) {
    this.sources = sources;
    this.launcher = launcher;
    this.version = version;
  }

  static PyIdeInfo fromProto(IntellijIdeInfo.PyIdeInfo proto) {
    String launcherString = Strings.emptyToNull(proto.getLauncher());
    Label launcher = null;
    if (launcherString != null) {
      launcher = Label.createIfValid(launcherString);
    }
    return new PyIdeInfo(
        ProtoWrapper.map(proto.getSourcesList(), ArtifactLocation::fromProto),
        launcher,
        proto.getPythonVersion());
  }

  @Override
  public IntellijIdeInfo.PyIdeInfo toProto() {
    IntellijIdeInfo.PyIdeInfo.Builder builder =
        IntellijIdeInfo.PyIdeInfo.newBuilder().addAllSources(ProtoWrapper.mapToProtos(sources));
    if (launcher != null) {
      builder.setLauncher(launcher.toString());
    }
    return builder.build();
  }

  public ImmutableList<ArtifactLocation> getSources() {
    return sources;
  }

  @Nullable
  public Label getLauncher() {
    return launcher;
  }

  public PythonVersion getPythonVersion() {
    return version;
  }

  public static Builder builder() {
    return new Builder();
  }

  /** Builder for python rule info */
  public static class Builder {
    private final ImmutableList.Builder<ArtifactLocation> sources = ImmutableList.builder();
    @Nullable Label launcher;
    private PythonVersion version;

    public Builder addSources(Iterable<ArtifactLocation> sources) {
      this.sources.addAll(sources);
      return this;
    }

    public Builder setLauncher(@Nullable String launcher) {
      this.launcher = (launcher == null) ? null : Label.createIfValid(launcher);
      return this;
    }

    public Builder setPythonVersion(PythonVersion version) {
      this.version = version;
      return this;
    }

    public PyIdeInfo build() {
      return new PyIdeInfo(sources.build(), launcher, version);
    }
  }

  @Override
  public String toString() {
    StringBuilder s = new StringBuilder();
    s.append("PyIdeInfo{\n");
    s.append("  sources=").append(getSources()).append("\n");
    Label l = getLauncher();
    if (l != null) {
      s.append("  launcher=").append(l).append("\n");
    }
    s.append("  python_version = ").append(version.toString()).append("\n");
    s.append("}");
    return s.toString();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    PyIdeInfo pyIdeInfo = (PyIdeInfo) o;
    return Objects.equals(sources, pyIdeInfo.sources)
        && Objects.equals(launcher, pyIdeInfo.launcher);
  }

  @Override
  public int hashCode() {
    return Objects.hash(sources, launcher);
  }
}
