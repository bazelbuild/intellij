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
package com.google.idea.blaze.base.sync.aspects;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableSet;
import com.google.devtools.intellij.aspect.Common;
import com.google.devtools.intellij.ideinfo.IntellijIdeInfo;
import com.google.devtools.intellij.ideinfo.IntellijIdeInfo.AndroidIdeInfo;
import com.google.devtools.intellij.ideinfo.IntellijIdeInfo.Dependency;
import com.google.devtools.intellij.ideinfo.IntellijIdeInfo.JavaIdeInfo;
import com.google.devtools.intellij.ideinfo.IntellijIdeInfo.LibraryArtifact;
import com.google.devtools.intellij.ideinfo.IntellijIdeInfo.TargetKey;
import com.google.idea.blaze.base.BlazeTestCase;
import com.google.idea.blaze.base.ideinfo.AndroidResFolder;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.io.FileOperationProvider;
import com.google.idea.common.experiments.ExperimentService;
import com.google.idea.common.experiments.MockExperimentService;
import java.util.Collection;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link BlazeIdeInterfaceAspectsImpl}. */
@RunWith(JUnit4.class)
public class BlazeIdeInterfaceAspectsImplTest extends BlazeTestCase {

  @Override
  protected void initTest(Container applicationServices, Container projectServices) {
    super.initTest(applicationServices, projectServices);
    applicationServices.register(ExperimentService.class, new MockExperimentService());
    applicationServices.register(FileOperationProvider.class, new FileOperationProvider());
  }

  @Test
  public void testTargetIdeInfoMultipleResourceFiles() {
    Common.ArtifactLocation localResFolder = artifactLocation("res");
    Set<String> localResourceRelativePath = ImmutableSet.of();
    Common.ArtifactLocation quantumResFolder =
        artifactLocation("java/com/google/android/assets/quantum/res");
    Set<String> quantumResRelativePath =
        ImmutableSet.of("values/colors.xml", "drawable/quantum_ic_1k_plus_vd_theme_24.xml");
    IntellijIdeInfo.TargetIdeInfo ideProto =
        IntellijIdeInfo.TargetIdeInfo.newBuilder()
            .setKey(TargetKey.newBuilder().setLabel("//test:test").build())
            .setKindString("android_binary")
            .addDeps(
                Dependency.newBuilder()
                    .setTarget(TargetKey.newBuilder().setLabel("//test:dep"))
                    .build())
            .setJavaIdeInfo(
                JavaIdeInfo.newBuilder()
                    .addJars(
                        LibraryArtifact.newBuilder().setJar(artifactLocation("jar.jar")).build())
                    .addGeneratedJars(
                        LibraryArtifact.newBuilder().setJar(artifactLocation("jar.jar")).build())
                    .addSources(artifactLocation("source.java")))
            .setAndroidIdeInfo(
                AndroidIdeInfo.newBuilder()
                    .addResFolders(resFolderLocation(localResFolder, localResourceRelativePath))
                    .addResFolders(resFolderLocation(quantumResFolder, quantumResRelativePath))
                    .setApk(artifactLocation("apk"))
                    .addDependencyApk(artifactLocation("apk"))
                    .setJavaPackage("package"))
            .build();
    TargetIdeInfo target = TargetIdeInfo.fromProto(ideProto);
    assertThat(target).isNotNull();
    Collection<AndroidResFolder> resources = target.getAndroidIdeInfo().getResFolders();
    assertThat(resources)
        .containsExactly(
            resFolderLocation(artifactLocation(localResFolder), localResourceRelativePath),
            resFolderLocation(artifactLocation(quantumResFolder), quantumResRelativePath));
  }

  static ArtifactLocation artifactLocation(Common.ArtifactLocation artifactLocation) {
    return ArtifactLocation.builder().setRelativePath(artifactLocation.getRelativePath()).build();
  }

  static Common.ArtifactLocation artifactLocation(String relativePath) {
    return Common.ArtifactLocation.newBuilder().setRelativePath(relativePath).build();
  }

  static AndroidResFolder resFolderLocation(ArtifactLocation root, Set<String> resources) {
    return AndroidResFolder.builder().setRoot(root).addResources(resources).build();
  }

  static IntellijIdeInfo.ResFolderLocation resFolderLocation(
      Common.ArtifactLocation root, Set<String> resources) {
    return IntellijIdeInfo.ResFolderLocation.newBuilder()
        .setRoot(root)
        .addAllResources(resources)
        .build();
  }
}
