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
import com.google.idea.blaze.base.BlazeTestCase;
import com.google.idea.blaze.base.TestUtils;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetMap;
import com.google.idea.blaze.base.io.FileAttributeProvider;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.common.experiments.ExperimentService;
import com.google.idea.common.experiments.MockExperimentService;
import com.google.repackaged.devtools.intellij.ideinfo.IntellijIdeInfo;
import java.io.File;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link BlazeIdeInterfaceAspectsImpl}. */
@RunWith(JUnit4.class)
public class BlazeIdeInterfaceAspectsImplTest extends BlazeTestCase {

  @Override
  protected void initTest(
      @NotNull Container applicationServices, @NotNull Container projectServices) {
    super.initTest(applicationServices, projectServices);
    applicationServices.register(ExperimentService.class, new MockExperimentService());
    applicationServices.register(FileAttributeProvider.class, new FileAttributeProvider());
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

  static IntellijIdeInfo.ArtifactLocation artifactLocation(String relativePath) {
    return IntellijIdeInfo.ArtifactLocation.newBuilder().setRelativePath(relativePath).build();
  }
}
