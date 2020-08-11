/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.android.sync;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.truth.Truth.assertThat;
import static com.google.idea.blaze.android.targetmapbuilder.NbAndroidTarget.android_binary;
import static com.google.idea.blaze.android.targetmapbuilder.NbAndroidTarget.android_library;
import static com.google.idea.blaze.android.targetmapbuilder.NbTargetBuilder.targetMap;
import static com.google.idea.blaze.java.AndroidBlazeRules.RuleTypes.ANDROID_BINARY;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.android.BlazeAndroidIntegrationTestCase;
import com.google.idea.blaze.android.MockSdkUtil;
import com.google.idea.blaze.android.sdk.BlazeSdkProvider;
import com.google.idea.blaze.android.sync.sdk.AndroidSdkFromProjectView;
import com.google.idea.blaze.android.sync.sdk.SdkUtil;
import com.google.idea.blaze.base.TestUtils;
import com.google.idea.blaze.base.dependencies.TargetInfo;
import com.google.idea.blaze.base.ideinfo.TargetMap;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.model.primitives.WorkspaceType;
import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration;
import com.google.idea.blaze.base.run.BlazeCommandRunConfigurationType;
import com.google.idea.blaze.base.sync.BlazeBuildParams;
import com.google.idea.blaze.base.sync.BlazeSyncParams;
import com.google.idea.blaze.base.sync.SyncMode;
import com.google.idea.blaze.base.sync.data.BlazeDataStorage;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.base.sync.projectstructure.ModuleFinder;
import com.google.idea.blaze.java.sync.BlazeJavaSyncAugmenter;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.impl.RunManagerImpl;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.util.containers.MultiMap;
import java.io.File;
import java.util.Arrays;
import java.util.List;
import org.jetbrains.android.facet.AndroidFacet;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Android-specific sync integration tests. This test also covers {@link
 * com.google.idea.blaze.android.sync.projectstructure.BlazeAndroidProjectStructureSyncer}
 */
@RunWith(JUnit4.class)
public class AndroidSyncTest extends BlazeAndroidIntegrationTestCase {

  private static final String ANDROID_28 = "android-28";

  private static final class TestProjectArguments {
    Sdk sdk;
    TargetMap targetMap;
    VirtualFile javaRoot;

    TestProjectArguments(Sdk sdk, TargetMap targetMap, VirtualFile javaRoot) {
      this.sdk = checkNotNull(sdk);
      this.targetMap = checkNotNull(targetMap);
      this.javaRoot = checkNotNull(javaRoot);
    }
  }

  public TestProjectArguments createTestProjectArguments() {
    Sdk android25 = MockSdkUtil.registerSdk(workspace, "25");

    RunManager runManager = RunManagerImpl.getInstanceImpl(getProject());
    RunnerAndConfigurationSettings runnerAndConfigurationSettings =
        runManager.createConfiguration(
            "Blaze Android Binary Run Configuration",
            BlazeCommandRunConfigurationType.getInstance().getFactory());
    runManager.addConfiguration(runnerAndConfigurationSettings, false);
    BlazeCommandRunConfiguration configuration =
        (BlazeCommandRunConfiguration) runnerAndConfigurationSettings.getConfiguration();
    TargetInfo target =
        TargetInfo.builder(
                Label.create("//java/com/android:app"), ANDROID_BINARY.getKind().getKindString())
            .build();
    configuration.setTargetInfo(target);

    workspace.createFile(
        new WorkspacePath("java/com/google/Source.java"),
        "package com.google;",
        "public class Source {}");
    workspace.createFile(
        new WorkspacePath("java/com/google/Other.java"),
        "package com.google;",
        "public class Other {}");
    VirtualFile javaRoot = workspace.createDirectory(new WorkspacePath("java/com/google"));
    TargetMap targetMap =
        targetMap(
            android_library("//java/com/google:lib")
                .java_toolchain_version("8")
                .res("res/values/strings.xml")
                .src("Source.java", "Other.java"),
            android_binary("//java/com/android:app"));
    return new TestProjectArguments(android25, targetMap, javaRoot);
  }

  @Test
  public void testAndroidSyncAugmenterPresent() {
    assertThat(
            Arrays.stream(BlazeJavaSyncAugmenter.EP_NAME.getExtensions())
                .anyMatch(e -> e instanceof BlazeAndroidJavaSyncAugmenter))
        .isTrue();
  }

  @Test
  public void testSimpleSync_invalidSdkAndFailToReInstall() {
    TestProjectArguments testEnvArgument = createTestProjectArguments();
    MockSdkUtil.registerSdk(workspace, "28", "5", MultiMap.create(), false);
    setProjectView(
        "directories:",
        "  java/com/google",
        "targets:",
        "  //java/com/google:lib",
        "android_sdk_platform: android-28");
    setTargetMap(testEnvArgument.targetMap);
    runBlazeSync(
        BlazeSyncParams.builder()
            .setTitle("Sync")
            .setSyncMode(SyncMode.INCREMENTAL)
            .setSyncOrigin("test")
            .setBlazeBuildParams(BlazeBuildParams.fromProject(getProject()))
            .setAddProjectViewTargets(true)
            .build());
    List<Sdk> allSdks = BlazeSdkProvider.getInstance().getAllAndroidSdks();
    assertThat(allSdks).containsExactly(testEnvArgument.sdk);
    errorCollector.assertIssues(
        String.format(
            AndroidSdkFromProjectView.NO_SDK_ERROR_TEMPLATE,
            ANDROID_28,
            Joiner.on(", ").join(AndroidSdkFromProjectView.getAvailableSdkTargetHashes(allSdks))));
    assertThat(ModuleManager.getInstance(getProject()).getModules()).isEmpty();
  }

  @Test
  public void testSimpleSync_invalidSdkAndSReInstall() {
    TestProjectArguments testEnvArgument = createTestProjectArguments();
    MockSdkUtil.registerSdk(workspace, "28", "5", MultiMap.create(), true);
    setProjectView(
        "directories:",
        "  java/com/google",
        "targets:",
        "  //java/com/google:lib",
        "android_sdk_platform: android-28");
    setTargetMap(testEnvArgument.targetMap);
    // When IDE re-add local SDK into {link @ProjectJdkTable}, it need access to embedded jdk. Set
    // path to mock jdk as embedded jdk path to avoid NPE.
    Sdk jdk = IdeaTestUtil.getMockJdk18();
    File jdkFile = new File(jdk.getHomePath());
    if (!jdkFile.exists()) {
      jdkFile.mkdirs();
      jdkFile.deleteOnExit();
      TestUtils.setSystemProperties(
          getTestRootDisposable(), "android.test.embedded.jdk", jdkFile.getPath());
    }
    runBlazeSync(
        BlazeSyncParams.builder()
            .setTitle("Sync")
            .setSyncMode(SyncMode.INCREMENTAL)
            .setSyncOrigin("test")
            .setBlazeBuildParams(BlazeBuildParams.fromProject(getProject()))
            .setAddProjectViewTargets(true)
            .build());
    assertSyncSuccess(testEnvArgument.targetMap, testEnvArgument.javaRoot);
    assertThat(SdkUtil.containsJarAndRes(BlazeSdkProvider.getInstance().findSdk(ANDROID_28)))
        .isTrue();
  }

  @Test
  public void testSimpleSync() {
    TestProjectArguments testEnvArgument = createTestProjectArguments();
    setProjectView(
        "directories:",
        "  java/com/google",
        "targets:",
        "  //java/com/google:lib",
        "android_sdk_platform: android-25");

    setTargetMap(testEnvArgument.targetMap);
    runBlazeSync(
        BlazeSyncParams.builder()
            .setTitle("Sync")
            .setSyncMode(SyncMode.INCREMENTAL)
            .setSyncOrigin("test")
            .setBlazeBuildParams(BlazeBuildParams.fromProject(getProject()))
            .setAddProjectViewTargets(true)
            .build());
    assertSyncSuccess(testEnvArgument.targetMap, testEnvArgument.javaRoot);
  }

  private void assertSyncSuccess(TargetMap targetMap, VirtualFile javaRoot) {
    errorCollector.assertNoIssues();
    BlazeProjectData blazeProjectData =
        BlazeProjectDataManager.getInstance(getProject()).getBlazeProjectData();
    assertThat(blazeProjectData).isNotNull();
    assertThat(blazeProjectData.getTargetMap()).isEqualTo(targetMap);
    assertThat(blazeProjectData.getWorkspaceLanguageSettings().getWorkspaceType())
        .isEqualTo(WorkspaceType.ANDROID);

    ImmutableList<ContentEntry> contentEntries = getWorkspaceContentEntries();
    assertThat(contentEntries).hasSize(1);
    assertThat(findContentEntry(javaRoot)).isNotNull();
    assertThat(findContentEntry(javaRoot).getSourceFolders()).hasLength(1);

    // Check that the workspace is set to android
    Module workspaceModule =
        ModuleFinder.getInstance(getProject())
            .findModuleByName(BlazeDataStorage.WORKSPACE_MODULE_NAME);
    assertThat(workspaceModule).isNotNull();
    assertThat(AndroidFacet.getInstance(workspaceModule)).isNotNull();

    // Check that a resource module was created
    Module resourceModule =
        ModuleFinder.getInstance(getProject()).findModuleByName("java.com.google.lib");
    assertThat(resourceModule).isNotNull();
    assertThat(AndroidFacet.getInstance(resourceModule)).isNotNull();

    // The default language level should be whatever is specified in the toolchain info
    assertThat(LanguageLevelProjectExtension.getInstance(getProject()).getLanguageLevel())
        .isEqualTo(LanguageLevel.JDK_1_8);
  }
}
