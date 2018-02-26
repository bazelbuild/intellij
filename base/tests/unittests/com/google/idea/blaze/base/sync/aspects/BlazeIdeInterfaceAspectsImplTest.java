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

import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableMap;
import com.google.devtools.intellij.aspect.Common;
import com.google.devtools.intellij.ideinfo.IntellijIdeInfo;
import com.google.idea.blaze.base.BlazeTestCase;
import com.google.idea.blaze.base.TestUtils;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetMap;
import com.google.idea.blaze.base.io.FileOperationProvider;
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.common.experiments.ExperimentService;
import com.google.idea.common.experiments.MockExperimentService;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.File;

import static com.google.common.truth.Truth.assertThat;

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
  public void testTargetIdeInfoIsSerializable() {
    IntellijIdeInfo.TargetIdeInfo ideProto =
        IntellijIdeInfo.TargetIdeInfo.newBuilder()
            .setLabel("//test:test")
            .setKindString("android_binary")
            .addDependencies("//test:dep")
            .addTags("tag")
            .setJavaIdeInfo(
                IntellijIdeInfo.JavaIdeInfo.newBuilder()
                    .addJars(
                        IntellijIdeInfo.LibraryArtifact.newBuilder()
                            .setJar(artifactLocation("jar.jar"))
                            .build())
                    .addGeneratedJars(
                        IntellijIdeInfo.LibraryArtifact.newBuilder()
                            .setJar(artifactLocation("jar.jar"))
                            .build())
                    .addSources(artifactLocation("source.java")))
            .setAndroidIdeInfo(
                IntellijIdeInfo.AndroidIdeInfo.newBuilder()
                    .addResources(artifactLocation("res"))
                    .setApk(artifactLocation("apk"))
                    .addDependencyApk(artifactLocation("apk"))
                    .setJavaPackage("package"))
            .build();

    TargetIdeInfo target = IdeInfoFromProtobuf.makeTargetIdeInfo(ideProto);
    TestUtils.assertIsSerializable(target);
  }

  @Test
  public void testKtToolchainIdeInfoRendered() {
    // not rendered when file not provided -- this should never happen really but don't want to break upstream if it
    // does
    IntellijIdeInfo.TargetIdeInfo ideProto =
        IntellijIdeInfo.TargetIdeInfo.newBuilder()
            .setLabel("//some:label")
            .setKindString(Kind.KT_TOOLCHAIN_IDE_INFO.toString())
            .build();
    TargetIdeInfo target = IdeInfoFromProtobuf.makeTargetIdeInfo(ideProto);
    assertThat(target).isNotNull();
    assertThat(target.ktToolchainIdeInfo).isNull();

    // verify the rendering occurs the file is present
    ideProto =
        IntellijIdeInfo.TargetIdeInfo.newBuilder()
            .setLabel("//some:label")
            .setKindString(Kind.KT_TOOLCHAIN_IDE_INFO.toString())
            .setKtToolchainIdeInfo(
                IntellijIdeInfo.KotlinToolchainIdeInfo.newBuilder()
                    .setJsonInfoFile(artifactLocation("some/path.json"))
                    .build())
            .build();

    target = IdeInfoFromProtobuf.makeTargetIdeInfo(ideProto);
    assertThat(target).isNotNull();
    assertThat(target.ktToolchainIdeInfo).isNotNull();
    assertThat(target.ktToolchainIdeInfo.location.relativePath).isEqualTo("some/path.json");
  }

  @Test
  public void testBlazeStateIsSerializable() {
    BlazeIdeInterfaceAspectsImpl.State state = new BlazeIdeInterfaceAspectsImpl.State();
    state.fileToTargetMapKey =
        ImmutableBiMap.of(
            new File("fileName"),
            TargetIdeInfo.builder().setLabel(Label.create("//test:test")).build().key);
    state.fileState = ImmutableMap.of();
    state.targetMap =
        new TargetMap(ImmutableMap.of()); // Tested separately in testRuleIdeInfoIsSerializable

    TestUtils.assertIsSerializable(state);
  }

  static Common.ArtifactLocation artifactLocation(String relativePath) {
    return Common.ArtifactLocation.newBuilder().setRelativePath(relativePath).build();
  }
}
