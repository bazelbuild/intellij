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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.base.BlazeTestCase;
import com.google.idea.blaze.base.TestUtils;
import com.google.idea.blaze.base.ideinfo.RuleIdeInfo;
import com.google.idea.blaze.base.io.FileAttributeProvider;
import com.google.idea.blaze.base.model.RuleMap;
import com.google.idea.blaze.base.model.primitives.ExecutionRootPath;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.model.primitives.WorkspaceType;
import com.google.idea.blaze.base.sync.projectview.WorkspaceLanguageSettings;
import com.google.idea.blaze.base.sync.workspace.ArtifactLocationDecoder;
import com.google.idea.blaze.base.sync.workspace.BlazeRoots;
import com.google.idea.blaze.base.sync.workspace.WorkspacePathResolverImpl;
import com.google.idea.common.experiments.ExperimentService;
import com.google.idea.common.experiments.MockExperimentService;
import com.google.repackaged.devtools.build.lib.ideinfo.androidstudio.AndroidStudioIdeInfo;
import java.io.File;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link BlazeIdeInterfaceAspectsImpl}. */
@RunWith(JUnit4.class)
public class BlazeIdeInterfaceAspectsImplTest extends BlazeTestCase {

  private static final File DUMMY_ROOT = new File("/");
  private static final WorkspaceRoot WORKSPACE_ROOT = new WorkspaceRoot(DUMMY_ROOT);
  private static final BlazeRoots BLAZE_ROOTS =
      new BlazeRoots(
          DUMMY_ROOT,
          ImmutableList.of(DUMMY_ROOT),
          new ExecutionRootPath("out/crosstool/bin"),
          new ExecutionRootPath("out/crosstool/gen"));
  private static final ArtifactLocationDecoder DUMMY_DECODER =
      new ArtifactLocationDecoder(
          BLAZE_ROOTS, new WorkspacePathResolverImpl(WORKSPACE_ROOT, BLAZE_ROOTS));

  @Override
  protected void initTest(
      @NotNull Container applicationServices, @NotNull Container projectServices) {
    super.initTest(applicationServices, projectServices);
    applicationServices.register(ExperimentService.class, new MockExperimentService());
    applicationServices.register(FileAttributeProvider.class, new FileAttributeProvider());
  }

  @Test
  public void testRuleIdeInfoIsSerializable() {
    AndroidStudioIdeInfo.RuleIdeInfo ideProto =
        AndroidStudioIdeInfo.RuleIdeInfo.newBuilder()
            .setLabel("//test:test")
            .setKindString("android_binary")
            .addDependencies("//test:dep")
            .addTags("tag")
            .setJavaRuleIdeInfo(
                AndroidStudioIdeInfo.JavaRuleIdeInfo.newBuilder()
                    .addJars(
                        AndroidStudioIdeInfo.LibraryArtifact.newBuilder()
                            .setJar(artifactLocation("jar.jar"))
                            .build())
                    .addGeneratedJars(
                        AndroidStudioIdeInfo.LibraryArtifact.newBuilder()
                            .setJar(artifactLocation("jar.jar"))
                            .build())
                    .addSources(artifactLocation("source.java")))
            .setAndroidRuleIdeInfo(
                AndroidStudioIdeInfo.AndroidRuleIdeInfo.newBuilder()
                    .addResources(artifactLocation("res"))
                    .setApk(artifactLocation("apk"))
                    .addDependencyApk(artifactLocation("apk"))
                    .setJavaPackage("package"))
            .build();

    WorkspaceLanguageSettings workspaceLanguageSettings =
        new WorkspaceLanguageSettings(
            WorkspaceType.ANDROID, ImmutableSet.of(LanguageClass.ANDROID));
    RuleIdeInfo ruleIdeInfo =
        IdeInfoFromProtobuf.makeRuleIdeInfo(workspaceLanguageSettings, DUMMY_DECODER, ideProto);
    TestUtils.assertIsSerializable(ruleIdeInfo);
  }

  @Test
  public void testBlazeStateIsSerializable() {
    BlazeIdeInterfaceAspectsImpl.State state = new BlazeIdeInterfaceAspectsImpl.State();
    state.fileToLabel = ImmutableMap.of(new File("fileName"), new Label("//java/com/test:test"));
    state.fileState = ImmutableMap.of();
    state.ruleMap =
        new RuleMap(ImmutableMap.of()); // Tested separately in testRuleIdeInfoIsSerializable

    TestUtils.assertIsSerializable(state);
  }

  static AndroidStudioIdeInfo.ArtifactLocation artifactLocation(String relativePath) {
    return artifactLocation(DUMMY_ROOT.toString(), relativePath);
  }

  static AndroidStudioIdeInfo.ArtifactLocation artifactLocation(
      String rootPath, String relativePath) {
    return AndroidStudioIdeInfo.ArtifactLocation.newBuilder()
        .setRelativePath(relativePath)
        .build();
  }
}
