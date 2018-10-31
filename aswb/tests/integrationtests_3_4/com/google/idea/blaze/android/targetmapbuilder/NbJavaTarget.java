/*
 * Copyright 2018 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.android.targetmapbuilder;

import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.ideinfo.JavaIdeInfo;
import com.google.idea.blaze.base.ideinfo.LibraryArtifact;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;

/**
 * Builder for a blaze java target's IDE info. Defines common attributes across all java targets.
 * This builder accumulates attributes to a {@link TargetIdeInfo.Builder} which can be used to build
 * {@link TargetMap}.
 */
public class NbJavaTarget implements TargetIdeInfoBuilderWrapper {
  private final NbTarget target;
  private final JavaIdeInfo.Builder javaIdeInfoBuilder;
  private final BlazeInfoData blazeInfoData;
  private final WorkspacePath blazePackage;

  public static NbJavaTarget java_library(String label) {
    return java_library(label, BlazeInfoData.DEFAULT);
  }

  public static NbJavaTarget java_library(String label, BlazeInfoData environment) {
    return new NbJavaTarget(environment, label, Kind.JAVA_LIBRARY);
  }

  NbJavaTarget(BlazeInfoData blazeInfoData, String label, Kind kind) {
    target = new NbTarget(blazeInfoData, label, kind);
    this.blazeInfoData = blazeInfoData;
    javaIdeInfoBuilder = new JavaIdeInfo.Builder();
    this.blazePackage = NbTargetMapUtils.blazePackageForLabel(label);
  }

  @Override
  public TargetIdeInfo.Builder getIdeInfoBuilder() {
    return target.getIdeInfoBuilder().setJavaInfo(javaIdeInfoBuilder);
  }

  public NbJavaTarget generated_jar(String jarLabel) {
    String jarPath = NbTargetMapUtils.workspacePathForLabel(blazePackage, jarLabel);
    ArtifactLocation jar =
        ArtifactLocation.builder()
            .setRootExecutionPathFragment(blazeInfoData.getBlazeBinaryPath())
            .setRelativePath(jarPath)
            .setIsSource(false)
            .build();
    javaIdeInfoBuilder.addJar(LibraryArtifact.builder().setClassJar(jar));
    return this;
  }

  public NbJavaTarget src(String... sourceLabels) {
    target.src(sourceLabels);
    return this;
  }

  public NbJavaTarget dep(String... targetLabels) {
    target.dep(targetLabels);
    return this;
  }

  public NbJavaTarget java_toolchain_version(String version) {
    target.java_toolchain_version(version);
    return this;
  }
}
