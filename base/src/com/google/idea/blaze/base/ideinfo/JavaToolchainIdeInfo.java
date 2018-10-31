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

import com.google.devtools.intellij.ideinfo.IntellijIdeInfo;
import javax.annotation.Nullable;

/** Represents the java_toolchain class */
public final class JavaToolchainIdeInfo
    implements ProtoWrapper<IntellijIdeInfo.JavaToolchainIdeInfo> {
  private final String sourceVersion;
  private final String targetVersion;
  @Nullable private final ArtifactLocation javacJar;

  private JavaToolchainIdeInfo(
      String sourceVersion, String targetVersion, @Nullable ArtifactLocation javacJar) {
    this.sourceVersion = sourceVersion;
    this.targetVersion = targetVersion;
    this.javacJar = javacJar;
  }

  static JavaToolchainIdeInfo fromProto(IntellijIdeInfo.JavaToolchainIdeInfo proto) {
    return new JavaToolchainIdeInfo(
        proto.getSourceVersion(),
        proto.getTargetVersion(),
        proto.hasJavacJar() ? ArtifactLocation.fromProto(proto.getJavacJar()) : null);
  }

  @Override
  public IntellijIdeInfo.JavaToolchainIdeInfo toProto() {
    IntellijIdeInfo.JavaToolchainIdeInfo.Builder builder =
        IntellijIdeInfo.JavaToolchainIdeInfo.newBuilder()
            .setSourceVersion(sourceVersion)
            .setTargetVersion(targetVersion);
    ProtoWrapper.unwrapAndSetIfNotNull(builder::setJavacJar, javacJar);
    return builder.build();
  }

  public String getSourceVersion() {
    return sourceVersion;
  }

  public String getTargetVersion() {
    return targetVersion;
  }

  @Nullable
  public ArtifactLocation getJavacJar() {
    return javacJar;
  }

  @Override
  public String toString() {
    return "JavaToolchainIdeInfo{"
        + "\n"
        + "  sourceVersion="
        + getSourceVersion()
        + "\n"
        + "  targetVersion="
        + getTargetVersion()
        + "\n"
        + "  javacJar="
        + getJavacJar()
        + "\n"
        + '}';
  }

  public static Builder builder() {
    return new Builder();
  }

  /** Builder for java toolchain info */
  public static class Builder {
    String sourceVersion;
    String targetVersion;
    ArtifactLocation javacJar;

    public Builder setSourceVersion(String sourceVersion) {
      this.sourceVersion = sourceVersion;
      return this;
    }

    public Builder setTargetVersion(String targetVersion) {
      this.targetVersion = targetVersion;
      return this;
    }

    public Builder setJavacJar(ArtifactLocation javacJar) {
      this.javacJar = javacJar;
      return this;
    }

    public JavaToolchainIdeInfo build() {
      return new JavaToolchainIdeInfo(sourceVersion, targetVersion, javacJar);
    }
  }
}
