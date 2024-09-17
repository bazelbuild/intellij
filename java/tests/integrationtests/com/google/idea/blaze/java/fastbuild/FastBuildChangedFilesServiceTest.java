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
package com.google.idea.blaze.java.fastbuild;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.util.concurrent.MoreExecutors.newDirectExecutorService;
import static java.util.stream.Collectors.toSet;
import static org.junit.Assert.fail;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.SettableFuture;
import com.google.devtools.intellij.aspect.Common.ArtifactLocation;
import com.google.devtools.intellij.aspect.FastBuildInfo;
import com.google.idea.blaze.base.BlazeIntegrationTestCase;
import com.google.idea.blaze.base.command.info.BlazeInfo;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.MockBlazeProjectDataBuilder;
import com.google.idea.blaze.base.model.MockBlazeProjectDataManager;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.settings.BuildSystemName;
import com.google.idea.blaze.base.sync.workspace.ArtifactLocationDecoder;
import com.google.idea.blaze.java.fastbuild.FastBuildBlazeData.JavaInfo;
import com.google.idea.blaze.java.fastbuild.FastBuildChangedFilesService.ChangedSources;
import com.google.idea.blaze.java.fastbuild.FastBuildState.BuildOutput;
import java.io.File;
import java.util.Arrays;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link FastBuildChangedFilesService}. */
@RunWith(JUnit4.class)
public class FastBuildChangedFilesServiceTest extends BlazeIntegrationTestCase {

  private static final String BLAZE_EXECROOT = "/blaze/execution-root";
  private static final String BLAZE_BIN = BLAZE_EXECROOT + "/blaze-bin";
  private static final String BLAZE_TESTLOGS = BLAZE_EXECROOT + "/testlog";
  private static final BlazeInfo BLAZE_INFO =
      BlazeInfo.create(
          BuildSystemName.Blaze,
          ImmutableMap.of(
              "blaze-bin", BLAZE_BIN,
              "blaze-genfiles", BLAZE_EXECROOT + "/blaze-genfiles",
              "blaze-testlogs", BLAZE_TESTLOGS,
              "execution_root", BLAZE_EXECROOT,
              "output_base", "/blaze/output-base",
              "output_path", "/blaze/output-base/output-path"
          ));

  private FastBuildChangedFilesService changedFilesService;
  private ArtifactLocationDecoder artifactLocationDecoder;

  @Before
  public void setUpProjectData() {
    BlazeProjectData blazeProjectData = MockBlazeProjectDataBuilder.builder(workspaceRoot).build();
    artifactLocationDecoder = blazeProjectData.getArtifactLocationDecoder();
    changedFilesService =
        FastBuildChangedFilesService.createForTests(
            getProject(),
            new MockBlazeProjectDataManager(blazeProjectData),
            newDirectExecutorService());
  }

  @Test
  public void throwsOnUnknownLabel() {
    try {
      changedFilesService.getAndResetChangedSources(Label.create("//java:all_files"));
      fail("should have thrown");
    } catch (IllegalStateException e) {
      assertThat(e.getMessage()).contains("No build information");
    }
  }

  @Test
  public void returnsAllFilesOnUnfinishedBuild() {
    changedFilesService.newBuild(Label.create("//java:all_files"), SettableFuture.create());
    workspace.createFile(WorkspacePath.createIfValid("java/com/google/Hello.java"));
    workspace.createFile(WorkspacePath.createIfValid("java/com/google/Goodbye.java"));

    ChangedSources changedSources =
        changedFilesService.getAndResetChangedSources(Label.create("//java:all_files"));

    assertThat(changedSources.needsFullCompile()).isFalse();
    assertThat(changedSources.changedSources())
        .containsExactly(
            artifactLocationDecoder.decode(source("java/com/google/Hello.java")),
            artifactLocationDecoder.decode(source("java/com/google/Goodbye.java")));
  }

  @Test
  public void failedBuildResetsToUnknownState() {
    changedFilesService.newBuild(
        Label.create("//java:all_files"),
        Futures.immediateFailedFuture(new RuntimeException("bad build")));

    try {
      changedFilesService.getAndResetChangedSources(Label.create("//java:all_files"));
      fail("should have thrown");
    } catch (IllegalStateException e) {
      assertThat(e.getMessage()).contains("No build information");
    }
  }

  @Test
  public void successfulBuildWithNoSources() {
    changedFilesService.newBuild(
        Label.create("//java:all_files"),
        Futures.immediateFuture(
            BuildOutput.create(
                new File("deploy.jar"), /* blazeData= */ ImmutableMap.of(), BLAZE_INFO)));

    ChangedSources changedSources =
        changedFilesService.getAndResetChangedSources(Label.create("//java:all_files"));

    assertThat(changedSources.needsFullCompile()).isFalse();
    assertThat(changedSources.changedSources()).isEmpty();
  }

  @Test
  public void successfulBuildWithNoModifications() {
    BuildOutput buildOutput =
        BuildOutput.create(
            new File("deploy.jar"),
            ImmutableMap.of(
                Label.create("//java:all_files"), sources("java/com/google/Hello.java").build()),
            BLAZE_INFO);
    changedFilesService.newBuild(
        Label.create("//java:all_files"), Futures.immediateFuture(buildOutput));

    ChangedSources changedSources =
        changedFilesService.getAndResetChangedSources(Label.create("//java:all_files"));

    assertThat(changedSources.needsFullCompile()).isFalse();
    assertThat(changedSources.changedSources()).isEmpty();
  }

  @Test
  public void successfulBuildWithSingleModification() {
    BuildOutput buildOutput =
        BuildOutput.create(
            new File("deploy.jar"),
            ImmutableMap.of(
                Label.create("//java:all_files"), sources("java/com/google/Hello.java").build()),
            BLAZE_INFO);
    changedFilesService.newBuild(
        Label.create("//java:all_files"), Futures.immediateFuture(buildOutput));

    workspace.createFile(WorkspacePath.createIfValid("java/com/google/Hello.java"));

    ChangedSources changedSources =
        changedFilesService.getAndResetChangedSources(Label.create("//java:all_files"));

    assertThat(changedSources.needsFullCompile()).isFalse();
    assertThat(changedSources.changedSources())
        .containsExactly(artifactLocationDecoder.decode(source("java/com/google/Hello.java")));
  }

  @Test
  public void successfulBuildWithModificationBeforeCompileFinishes() {
    SettableFuture<BuildOutput> buildOutput = SettableFuture.create();
    changedFilesService.newBuild(Label.create("//java:all_files"), buildOutput);

    workspace.createFile(WorkspacePath.createIfValid("java/com/google/Hello.java"));

    buildOutput.set(
        BuildOutput.create(
            new File("deploy.jar"),
            ImmutableMap.of(
                Label.create("//java:all_files"), sources("java/com/google/Hello.java").build()),
            BLAZE_INFO));

    ChangedSources changedSources =
        changedFilesService.getAndResetChangedSources(Label.create("//java:all_files"));

    assertThat(changedSources.needsFullCompile()).isFalse();
    assertThat(changedSources.changedSources())
        .containsExactly(artifactLocationDecoder.decode(source("java/com/google/Hello.java")));
  }

  @Test
  public void getSourcesClearsModifiedList() {
    BuildOutput buildOutput =
        BuildOutput.create(
            new File("deploy.jar"),
            ImmutableMap.of(
                Label.create("//java:all_files"), sources("java/com/google/Hello.java").build()),
            BLAZE_INFO);
    changedFilesService.newBuild(
        Label.create("//java:all_files"), Futures.immediateFuture(buildOutput));

    workspace.createFile(WorkspacePath.createIfValid("java/com/google/Hello.java"));

    changedFilesService.getAndResetChangedSources(Label.create("//java:all_files"));

    ChangedSources changedSources =
        changedFilesService.getAndResetChangedSources(Label.create("//java:all_files"));

    assertThat(changedSources.needsFullCompile()).isFalse();
    assertThat(changedSources.changedSources()).isEmpty();
  }

  @Test
  public void graphOfLabels() {
    // A--> B -> D
    //  \
    //   \-> C -> B -> D
    BuildOutput buildOutput =
        BuildOutput.create(
            new File("deploy.jar"),
            ImmutableMap.of(
                Label.create("//a:a"),
                    sources("a.java").setDependencies(deps("//b:b", "//c:c")).build(),
                Label.create("//b:b"), sources("b.java").setDependencies(deps("//d:d")).build(),
                Label.create("//c:c"), sources("c.java").setDependencies(deps("//b:b")).build(),
                Label.create("//d:d"), sources("d.java").build()),
            BLAZE_INFO);
    changedFilesService.newBuild(Label.create("//a:a"), Futures.immediateFuture(buildOutput));

    workspace.createFile(WorkspacePath.createIfValid("d.java"));

    ChangedSources changedSources =
        changedFilesService.getAndResetChangedSources(Label.create("//a:a"));

    assertThat(changedSources.needsFullCompile()).isFalse();
    assertThat(changedSources.changedSources())
        .containsExactly(artifactLocationDecoder.decode(source("d.java")));
  }

  @Test
  public void unknownFilesAreIgnored() {
    changedFilesService.newBuild(
        Label.create("//java:all_files"),
        Futures.immediateFuture(
            BuildOutput.create(
                new File("deploy.jar"),
                ImmutableMap.of(
                    Label.create("//java:all_files"), sources("One.java", "Two.java").build()),
                BLAZE_INFO)));

    workspace.createFile(WorkspacePath.createIfValid("One.java"));
    workspace.createFile(WorkspacePath.createIfValid("Three.java"));

    ChangedSources changedSources =
        changedFilesService.getAndResetChangedSources(Label.create("//java:all_files"));

    assertThat(changedSources.needsFullCompile()).isFalse();
    assertThat(changedSources.changedSources())
        .containsExactly(artifactLocationDecoder.decode(source("One.java")));
  }

  @Test
  public void unknownFilesAreIgnored_modifiedBeforeCompilationFinishes() {
    SettableFuture<BuildOutput> buildOutput = SettableFuture.create();
    changedFilesService.newBuild(Label.create("//java:all_files"), buildOutput);

    workspace.createFile(WorkspacePath.createIfValid("One.java"));
    workspace.createFile(WorkspacePath.createIfValid("Three.java"));

    buildOutput.set(
        BuildOutput.create(
            new File("deploy.jar"),
            ImmutableMap.of(
                Label.create("//java:all_files"), sources("One.java", "Two.java").build()),
            BLAZE_INFO));

    ChangedSources changedSources =
        changedFilesService.getAndResetChangedSources(Label.create("//java:all_files"));

    assertThat(changedSources.needsFullCompile()).isFalse();
    assertThat(changedSources.changedSources())
        .containsExactly(artifactLocationDecoder.decode(source("One.java")));
  }

  @Test
  public void suggestsRecompilation() {
    String[] filenames = new String[FastBuildChangedFilesService.MAX_FILES_TO_COLLECT + 1];
    Arrays.setAll(filenames, i -> "File" + i + ".java");

    BuildOutput buildOutput =
        BuildOutput.create(
            new File("deploy.jar"),
            ImmutableMap.of(Label.create("//java:all_files"), sources(filenames).build()),
            BLAZE_INFO);
    changedFilesService.newBuild(
        Label.create("//java:all_files"), Futures.immediateFuture(buildOutput));

    Arrays.stream(filenames).map(WorkspacePath::createIfValid).forEach(workspace::createFile);

    ChangedSources changedSources =
        changedFilesService.getAndResetChangedSources(Label.create("//java:all_files"));

    assertThat(changedSources.needsFullCompile()).isTrue();
  }

  @Test
  public void suggestsRecompilation_modifiedBeforeCompilationFinishes() {

    SettableFuture<BuildOutput> buildOutput = SettableFuture.create();
    changedFilesService.newBuild(Label.create("//java:all_files"), buildOutput);

    String[] filenames = new String[FastBuildChangedFilesService.MAX_FILES_TO_COLLECT + 1];
    Arrays.setAll(filenames, i -> "File" + i + ".java");

    Arrays.stream(filenames).map(WorkspacePath::createIfValid).forEach(workspace::createFile);

    buildOutput.set(
        BuildOutput.create(
            new File("deploy.jar"),
            ImmutableMap.of(Label.create("//java:all_files"), sources(filenames).build()),
            BLAZE_INFO));

    ChangedSources changedSources =
        changedFilesService.getAndResetChangedSources(Label.create("//java:all_files"));

    assertThat(changedSources.needsFullCompile()).isTrue();
  }

  @Test
  public void addFilesFromFailedCompilation() {
    changedFilesService.newBuild(Label.create("//java:all_files"), SettableFuture.create());
    changedFilesService.addFilesFromFailedCompilation(
        Label.create("//java:all_files"),
        ImmutableSet.of(new File("/foo/bar.txt"), new File("/what/fun.txt")));

    ChangedSources changedSources =
        changedFilesService.getAndResetChangedSources(Label.create("//java:all_files"));

    assertThat(changedSources.needsFullCompile()).isFalse();
    assertThat(changedSources.changedSources())
        .containsExactly(new File("/foo/bar.txt"), new File("/what/fun.txt"));
  }

  private static com.google.idea.blaze.base.ideinfo.ArtifactLocation source(String relativePath) {
    return com.google.idea.blaze.base.ideinfo.ArtifactLocation.fromProto(
        protoSourceArtifact(relativePath));
  }

  private static ArtifactLocation protoSourceArtifact(String relativePath) {
    return ArtifactLocation.newBuilder().setRelativePath(relativePath).setIsSource(true).build();
  }

  private static FastBuildBlazeData.Builder sources(String... artifacts) {
    Set<ArtifactLocation> sourceArtifacts =
        Arrays.stream(artifacts)
            .map(FastBuildChangedFilesServiceTest::protoSourceArtifact)
            .collect(toSet());
    return FastBuildBlazeData.builder()
        .setLabel(Label.create("//ignore:ignore"))
        .setWorkspaceName("io_bazel")
        .setJavaInfo(
            JavaInfo.fromProto(
                FastBuildInfo.JavaInfo.newBuilder().addAllSources(sourceArtifacts).build()));
  }

  private static ImmutableList<Label> deps(String... deps) {
    return Arrays.stream(deps).map(Label::create).collect(toImmutableList());
  }
}
