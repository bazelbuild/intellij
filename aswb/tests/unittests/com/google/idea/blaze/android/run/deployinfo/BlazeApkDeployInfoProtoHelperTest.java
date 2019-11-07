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
package com.google.idea.blaze.android.run.deployinfo;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.devtools.build.lib.rules.android.deployinfo.AndroidDeployInfoOuterClass;
import com.google.devtools.build.lib.rules.android.deployinfo.AndroidDeployInfoOuterClass.AndroidDeployInfo;
import com.google.devtools.build.lib.rules.android.deployinfo.AndroidDeployInfoOuterClass.Artifact;
import com.google.idea.blaze.android.manifest.ManifestParser.ParsedManifest;
import com.google.idea.blaze.android.manifest.ParsedManifestService;
import com.google.idea.blaze.android.run.deployinfo.BlazeApkDeployInfoProtoHelper.AndroidDeployInfoReader;
import com.google.idea.blaze.base.BlazeTestCase;
import com.google.idea.blaze.base.bazel.BuildSystemProvider;
import com.google.idea.blaze.base.command.buildresult.BuildResultHelper;
import com.google.idea.blaze.base.command.info.BlazeInfo;
import com.google.idea.blaze.base.command.info.BlazeInfoRunner;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.settings.BlazeImportSettings;
import com.google.idea.blaze.base.settings.BlazeImportSettingsManager;
import com.google.idea.blaze.base.settings.BuildSystem;
import com.intellij.openapi.extensions.ExtensionPoint;
import java.io.File;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import kotlin.text.Charsets;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

/** Unit tests for {@link BlazeApkDeployInfoProtoHelper} */
@RunWith(JUnit4.class)
public class BlazeApkDeployInfoProtoHelperTest extends BlazeTestCase {
  private static final BlazeImportSettings DUMMY_IMPORT_SETTINGS =
      new BlazeImportSettings("root", "", "", "", BuildSystem.Bazel);
  private static final String TEST_EXECUTION_ROOT = "execution_root";

  private final ParsedManifestService mockParsedManifestService =
      Mockito.mock(ParsedManifestService.class);
  private final BlazeContext context = new BlazeContext();

  /**
   * Initializes the following extensions/services:
   *
   * <p>Registers the {@link BuildSystemProvider} extension point with a mock implementation that
   * returns a fake binary path. This is required for obtaining the execution root (which runs blaze
   * info and needs to know where the blaze executable is).
   *
   * <p>Registers a {@link BlazeImportSettingsManager} with dummy settings. This is required for
   * finding the correct workspace root as a part of proto helper init.
   *
   * <p>Registers a {@link MockBlazeInfoRunner} that quickly returns {@link #TEST_EXECUTION_ROOT}.
   * This is required by the proto helper to obtain test execution root path.
   *
   * <p>Registers a mocked {@link ParsedManifestService} to return predetermined matching parsed
   * manifests and verify manifest refreshes are called correctly.
   */
  @Override
  protected void initTest(Container applicationServices, Container projectServices) {
    ExtensionPoint<BuildSystemProvider> extensionPoint =
        registerExtensionPoint(BuildSystemProvider.EP_NAME, BuildSystemProvider.class);
    BuildSystemProvider mockBuildSystemProvider = Mockito.mock(BuildSystemProvider.class);
    when(mockBuildSystemProvider.getBinaryPath(project)).thenReturn("binary/path");
    extensionPoint.registerExtension(mockBuildSystemProvider);

    projectServices.register(
        BlazeImportSettingsManager.class, new BlazeImportSettingsManager(project));
    BlazeImportSettingsManager.getInstance(getProject()).setImportSettings(DUMMY_IMPORT_SETTINGS);

    MockBlazeInfoRunner blazeInfoRunner = new MockBlazeInfoRunner();
    blazeInfoRunner.setResults(ImmutableMap.of(BlazeInfo.EXECUTION_ROOT_KEY, TEST_EXECUTION_ROOT));
    applicationServices.register(BlazeInfoRunner.class, blazeInfoRunner);

    projectServices.register(ParsedManifestService.class, mockParsedManifestService);
  }

  @Test
  public void readDeployInfoForNormalBuild_onlyMainManifest() throws Exception {
    // setup
    AndroidDeployInfo deployInfoProto =
        AndroidDeployInfo.newBuilder()
            .setMergedManifest(makeArtifact("path/to/manifest"))
            .addApksToDeploy(makeArtifact("path/to/apk"))
            .build();

    Label target = Label.create("//test:target");
    File mainApk = new File("execution_root/path/to/apk");
    File mainManifestFile = new File("execution_root/path/to/manifest");
    ParsedManifest parsedMainManifest = new ParsedManifest("main", null, null);
    when(mockParsedManifestService.getParsedManifest(mainManifestFile))
        .thenReturn(parsedMainManifest);

    AndroidDeployInfoReader mockAndroidDeployInfoReader =
        Mockito.mock(AndroidDeployInfoReader.class);
    BuildResultHelper buildResultHelper = Mockito.mock(BuildResultHelper.class);
    when(mockAndroidDeployInfoReader.getDeployInfo(buildResultHelper, target))
        .thenReturn(deployInfoProto);

    // perform
    BlazeApkDeployInfoProtoHelper helper =
        new BlazeApkDeployInfoProtoHelper(
            getProject(), ImmutableList.of(), mockAndroidDeployInfoReader);
    BlazeAndroidDeployInfo deployInfo =
        helper.readDeployInfoForNormalBuild(context, buildResultHelper, target);

    // verify
    assertThat(deployInfo.getApksToDeploy()).containsExactly(mainApk);
    assertThat(deployInfo.getMergedManifest()).isEqualTo(parsedMainManifest);
    assertThat(deployInfo.getTestTargetMergedManifest()).isNull();
    verify(mockParsedManifestService, times(1)).invalidateCachedManifest(mainManifestFile);
  }

  @Test
  public void readDeployInfoForNormalBuild_withTestTargetManifest() throws Exception {
    // setup
    AndroidDeployInfo deployInfoProto =
        AndroidDeployInfo.newBuilder()
            .setMergedManifest(makeArtifact("path/to/manifest"))
            .addAdditionalMergedManifests(makeArtifact("path/to/testtarget/manifest"))
            .addApksToDeploy(makeArtifact("path/to/apk"))
            .addApksToDeploy(makeArtifact("path/to/testtarget/apk"))
            .build();

    Label target = Label.create("//test:target");
    File mainApk = new File("execution_root/path/to/apk");
    File testApk = new File("execution_root/path/to/testtarget/apk");
    File mainManifest = new File("execution_root/path/to/manifest");
    File testTargetManifest = new File("execution_root/path/to/testtarget/manifest");
    ParsedManifest parsedMainManifest = new ParsedManifest("main", null, null);
    ParsedManifest parsedTestManifest = new ParsedManifest("testtarget", null, null);
    when(mockParsedManifestService.getParsedManifest(mainManifest)).thenReturn(parsedMainManifest);
    when(mockParsedManifestService.getParsedManifest(testTargetManifest))
        .thenReturn(parsedTestManifest);

    AndroidDeployInfoReader mockAndroidDeployInfoReader =
        Mockito.mock(AndroidDeployInfoReader.class);
    BuildResultHelper buildResultHelper = Mockito.mock(BuildResultHelper.class);
    when(mockAndroidDeployInfoReader.getDeployInfo(buildResultHelper, target))
        .thenReturn(deployInfoProto);

    // perform
    BlazeApkDeployInfoProtoHelper helper =
        new BlazeApkDeployInfoProtoHelper(
            getProject(), ImmutableList.of(), mockAndroidDeployInfoReader);
    BlazeAndroidDeployInfo deployInfo =
        helper.readDeployInfoForNormalBuild(context, buildResultHelper, target);

    // verify
    assertThat(deployInfo.getApksToDeploy()).containsExactly(mainApk, testApk).inOrder();
    assertThat(deployInfo.getMergedManifest()).isEqualTo(parsedMainManifest);
    assertThat(deployInfo.getTestTargetMergedManifest()).isEqualTo(parsedTestManifest);

    ArgumentCaptor<File> expectedArgs = ArgumentCaptor.forClass(File.class);
    verify(mockParsedManifestService, times(2)).invalidateCachedManifest(expectedArgs.capture());
    expectedArgs.getAllValues().containsAll(ImmutableList.of(mainManifest, testTargetManifest));
  }

  @Test
  public void readDeployInfoForInstrumentationTest() throws Exception {
    AndroidDeployInfoReader mockAndroidDeployInfoReader =
        Mockito.mock(AndroidDeployInfoReader.class);
    BuildResultHelper buildResultHelper = Mockito.mock(BuildResultHelper.class);

    // Setup instrumentor
    AndroidDeployInfo instrumentorDeployInfoProto =
        AndroidDeployInfo.newBuilder()
            .setMergedManifest(makeArtifact("path/to/instrumentor/manifest"))
            .addApksToDeploy(makeArtifact("path/to/instrumentor/apk"))
            .build();
    Label instrumentorLabel = Label.create("//test:instrumentor");
    File instrumentorApk = new File("execution_root/path/to/instrumentor/apk");
    File instrumentorManifest = new File("execution_root/path/to/instrumentor/manifest");
    ParsedManifest parsedInstrumentorManifest = new ParsedManifest("test", null, null);
    when(mockParsedManifestService.getParsedManifest(instrumentorManifest))
        .thenReturn(parsedInstrumentorManifest);
    when(mockAndroidDeployInfoReader.getDeployInfo(buildResultHelper, instrumentorLabel))
        .thenReturn(instrumentorDeployInfoProto);

    // Setup test target
    AndroidDeployInfo testDeployInfoProto =
        AndroidDeployInfo.newBuilder()
            .setMergedManifest(makeArtifact("path/to/test/manifest"))
            .addApksToDeploy(makeArtifact("path/to/test/apk"))
            .build();
    Label testLabel = Label.create("//test:test");
    File testApk = new File("execution_root/path/to/test/apk");
    File testManifest = new File("execution_root/path/to/test/manifest");
    ParsedManifest parsedTestManifest = new ParsedManifest("main", null, null);
    when(mockParsedManifestService.getParsedManifest(testManifest)).thenReturn(parsedTestManifest);
    when(mockAndroidDeployInfoReader.getDeployInfo(buildResultHelper, testLabel))
        .thenReturn(testDeployInfoProto);

    // perform
    BlazeApkDeployInfoProtoHelper helper =
        new BlazeApkDeployInfoProtoHelper(
            getProject(), ImmutableList.of(), mockAndroidDeployInfoReader);
    BlazeAndroidDeployInfo deployInfo =
        helper.readDeployInfoForInstrumentationTest(
            context, buildResultHelper, instrumentorLabel, testLabel);

    // verify
    assertThat(deployInfo.getApksToDeploy()).containsExactly(instrumentorApk, testApk).inOrder();
    assertThat(deployInfo.getMergedManifest()).isEqualTo(parsedInstrumentorManifest);
    assertThat(deployInfo.getTestTargetMergedManifest()).isEqualTo(parsedTestManifest);

    ArgumentCaptor<File> expectedArgs = ArgumentCaptor.forClass(File.class);
    verify(mockParsedManifestService, times(2)).invalidateCachedManifest(expectedArgs.capture());
    expectedArgs.getAllValues().containsAll(ImmutableList.of(instrumentorManifest, testManifest));
  }

  private static Artifact makeArtifact(String execRootPath) {
    return AndroidDeployInfoOuterClass.Artifact.newBuilder().setExecRootPath(execRootPath).build();
  }

  /** Returns predefined results set by {@link MockBlazeInfoRunner#setResults(java.util.Map)} */
  private static class MockBlazeInfoRunner extends BlazeInfoRunner {
    private final Map<String, String> results = Maps.newHashMap();

    @Override
    public ListenableFuture<byte[]> runBlazeInfoGetBytes(
        @Nullable BlazeContext context,
        String binaryPath,
        WorkspaceRoot workspaceRoot,
        List<String> blazeFlags,
        String key) {
      return Futures.immediateFuture(results.get(key).getBytes(Charsets.UTF_8));
    }

    @Override
    public ListenableFuture<String> runBlazeInfo(
        @Nullable BlazeContext context,
        String binaryPath,
        WorkspaceRoot workspaceRoot,
        List<String> blazeFlags,
        String key) {
      return Futures.immediateFuture(results.get(key));
    }

    @Override
    public ListenableFuture<BlazeInfo> runBlazeInfo(
        @Nullable BlazeContext context,
        BuildSystem buildSystem,
        String binaryPath,
        WorkspaceRoot workspaceRoot,
        List<String> blazeFlags) {
      return Futures.immediateFuture(BlazeInfo.create(buildSystem, ImmutableMap.copyOf(results)));
    }

    public void setResults(Map<String, String> results) {
      this.results.clear();
      this.results.putAll(results);
    }
  }
}
