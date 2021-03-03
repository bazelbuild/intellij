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
package com.google.idea.blaze.java.run.fastbuild;

import static com.google.common.truth.Truth.assertThat;

import com.google.devtools.intellij.aspect.Common.ArtifactLocation;
import com.google.devtools.intellij.aspect.FastBuildInfo;
import com.google.idea.blaze.base.BlazeIntegrationTestCase;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.MockBlazeProjectDataBuilder;
import com.google.idea.blaze.base.model.MockBlazeProjectDataManager;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.base.sync.workspace.ArtifactLocationDecoder;
import com.google.idea.blaze.java.fastbuild.FastBuildBlazeData.JavaInfo;
import com.intellij.execution.ExecutionException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link FastBuildTestClassFinder}. */
@RunWith(JUnit4.class)
public class FastBuildTestClassFinderTest extends BlazeIntegrationTestCase {

  private ArtifactLocationDecoder artifactLocationDecoder;

  @Before
  public void setUpProjectData() {
    BlazeProjectData blazeProjectData =
        MockBlazeProjectDataBuilder.builder(workspaceRoot)
            // the default outputBase is outside our test fileSystem, so we can't create files there
            .setOutputBase(workspaceRoot.toString() + "/output-base")
            .build();
    artifactLocationDecoder = blazeProjectData.getArtifactLocationDecoder();
    registerProjectService(
        BlazeProjectDataManager.class, new MockBlazeProjectDataManager(blazeProjectData));
  }

  @Test
  public void getClassFromJavaInfo() throws ExecutionException {
    JavaInfo javaInfo =
        JavaInfo.fromProto(
            FastBuildInfo.JavaInfo.newBuilder().setTestClass("my.fake.TestClass").build());

    String testClass =
        FastBuildTestClassFinder.getInstance(getProject())
            .getTestClass(Label.create("//javatests/com/google/devtools:AllTests"), javaInfo);

    assertThat(testClass).isEqualTo("my.fake.TestClass");
  }

  // If we find a source file with the same class name as the rule, use that as the test class
  @Test
  public void getClassFromTargetSources() throws ExecutionException {

    ArtifactLocation irrelevantArtifact =
        ArtifactLocation.newBuilder()
            .setRelativePath("java/com/google/devtools/Irrelevant.java")
            .build();
    ArtifactLocation matchingClassNameArtifact =
        ArtifactLocation.newBuilder()
            .setRelativePath("java/com/google/devtools/AllTests.java")
            .build();
    JavaInfo javaInfo =
        JavaInfo.fromProto(
            FastBuildInfo.JavaInfo.newBuilder()
                .addSources(irrelevantArtifact)
                .addSources(matchingClassNameArtifact)
                .build());
    fileSystem.createPsiFile(
        artifactLocationDecoder.decode(toPojo(matchingClassNameArtifact)).getPath(),
        "package com.google.hello",
        "class AllTests {}");
    fileSystem.createPsiFile(
        artifactLocationDecoder.decode(toPojo(irrelevantArtifact)).getPath(),
        "package com.google.hello",
        "class Irrelevant {}");

    String testClass =
        FastBuildTestClassFinder.getInstance(getProject())
            .getTestClass(Label.create("//javatests:AllTests"), javaInfo);

    assertThat(testClass).isEqualTo("com.google.hello.AllTests");
  }

  // If no sources match, just look for a file with the same name in the Blaze package, and use that
  // as the test class.
  @Test
  public void getClassFromFoundSources() throws ExecutionException {

    JavaInfo javaInfo = JavaInfo.fromProto(FastBuildInfo.JavaInfo.getDefaultInstance());
    workspace.createPsiFile(
        new WorkspacePath("javatests/AllTests.java"),
        "package com.google.hello",
        "class AllTests {}");

    String testClass =
        FastBuildTestClassFinder.getInstance(getProject())
            .getTestClass(Label.create("//javatests:AllTests"), javaInfo);

    assertThat(testClass).isEqualTo("com.google.hello.AllTests");
  }

  private com.google.idea.blaze.base.ideinfo.ArtifactLocation toPojo(
      ArtifactLocation artifactLocation) {
    return com.google.idea.blaze.base.ideinfo.ArtifactLocation.fromProto(artifactLocation);
  }
}
