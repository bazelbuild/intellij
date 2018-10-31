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

import static com.google.idea.blaze.android.targetmapbuilder.NbTargetMapUtils.makeSourceArtifact;

import com.google.idea.blaze.base.ideinfo.AndroidIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;

/**
 * Builder for a blaze android target's IDE info. Defines common attributes across all android
 * targets. Android targets are like java targets, but with additional attributes like manifests and
 * resource classes. This builder accumulates attributes to a {@link TargetIdeInfo.Builder} which
 * can be used to build {@link TargetMap}.
 */
public class NbAndroidTarget implements TargetIdeInfoBuilderWrapper {
  private final NbJavaTarget javaTarget;
  private final AndroidIdeInfo.Builder androidIdeInfoBuilder;
  private final BlazeInfoData blazeInfoData;
  private final WorkspacePath blazePackage;

  public static NbAndroidTarget android_library(String label) {
    return android_library(label, BlazeInfoData.DEFAULT);
  }

  public static NbAndroidTarget android_library(String label, BlazeInfoData environment) {
    return new NbAndroidTarget(environment, label, Kind.ANDROID_LIBRARY);
  }

  public static NbAndroidTarget android_binary(String label) {
    return android_binary(label, BlazeInfoData.DEFAULT);
  }

  public static NbAndroidTarget android_binary(String label, BlazeInfoData environment) {
    return new NbAndroidTarget(environment, label, Kind.ANDROID_BINARY);
  }

  NbAndroidTarget(BlazeInfoData blazeInfoData, String label, Kind kind) {
    javaTarget = new NbJavaTarget(blazeInfoData, label, kind);
    this.blazeInfoData = blazeInfoData;
    this.androidIdeInfoBuilder = AndroidIdeInfo.builder();
    this.blazePackage = NbTargetMapUtils.blazePackageForLabel(label);
  }

  @Override
  public TargetIdeInfo.Builder getIdeInfoBuilder() {
    return javaTarget.getIdeInfoBuilder().setAndroidInfo(androidIdeInfoBuilder);
  }

  // TODO: Infer an android library's resource package name from it's resources.
  public NbAndroidTarget res_java_package(String packageName) {
    // Is resource package name is set then this target should also generate resource class.
    generateResourceClass(true);
    androidIdeInfoBuilder.setResourceJavaPackage(packageName);
    return this;
  }

  public NbAndroidTarget res(String... resourceLabels) {
    // Is resource package name is set then this target should also generate resource class.
    generateResourceClass(true);
    for (String resourceLabel : resourceLabels) {
      String resourcePath = NbTargetMapUtils.workspacePathForLabel(blazePackage, resourceLabel);
      androidIdeInfoBuilder.addResource(makeSourceArtifact(resourcePath));
    }
    return this;
  }

  public NbAndroidTarget manifest(String manifestLabel) {
    String manifestPath = NbTargetMapUtils.workspacePathForLabel(blazePackage, manifestLabel);
    androidIdeInfoBuilder.setManifestFile(makeSourceArtifact(manifestPath));
    return this;
  }

  public NbAndroidTarget generateResourceClass(boolean shouldGenerate) {
    androidIdeInfoBuilder.setGenerateResourceClass(shouldGenerate);
    return this;
  }

  public NbAndroidTarget generated_jar(String relativeJarPath) {
    javaTarget.generated_jar(relativeJarPath);
    return this;
  }

  public NbAndroidTarget src(String... sourceLabels) {
    javaTarget.src(sourceLabels);
    return this;
  }

  public NbAndroidTarget dep(String... targetLabels) {
    javaTarget.dep(targetLabels);
    return this;
  }

  public NbAndroidTarget java_toolchain_version(String version) {
    javaTarget.java_toolchain_version(version);
    return this;
  }
}
