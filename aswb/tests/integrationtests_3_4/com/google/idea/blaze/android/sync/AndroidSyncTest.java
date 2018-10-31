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

import static com.google.common.truth.Truth.assertThat;
import static com.google.idea.blaze.android.targetmapbuilder.NbAndroidTarget.android_binary;
import static com.google.idea.blaze.android.targetmapbuilder.NbAndroidTarget.android_library;
import static com.google.idea.blaze.android.targetmapbuilder.NbCcTarget.cc_library;
import static com.google.idea.blaze.android.targetmapbuilder.NbCcToolchain.cc_toolchain;
import static com.google.idea.blaze.android.targetmapbuilder.TargetIdeInfoBuilderWrapper.targetMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.idea.blaze.android.AndroidIntegrationTestSetupRule;
import com.google.idea.blaze.android.sdk.BlazeSdkProvider;
import com.google.idea.blaze.android.sdk.MockBlazeSdkProvider;
import com.google.idea.blaze.base.ideinfo.CToolchainIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetKey;
import com.google.idea.blaze.base.ideinfo.TargetMap;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.model.primitives.WorkspaceType;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.sync.BlazeSyncIntegrationTestCase;
import com.google.idea.blaze.base.sync.BlazeSyncParams;
import com.google.idea.blaze.base.sync.BlazeSyncParams.SyncMode;
import com.google.idea.blaze.base.sync.data.BlazeDataStorage;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.base.sync.projectstructure.ModuleFinder;
import com.google.idea.blaze.cpp.BlazeCWorkspace;
import com.google.idea.blaze.cpp.BlazeConfigurationToolchainResolver;
import com.google.idea.blaze.cpp.CompilerVersionChecker;
import com.google.idea.blaze.cpp.MockCompilerVersionChecker;
import com.google.idea.blaze.java.sync.BlazeJavaSyncAugmenter;
import com.google.idea.sdkcompat.cidr.OCWorkspaceManagerAdapter;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkTypeId;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.jetbrains.cidr.lang.workspace.OCWorkspace;
import java.util.Arrays;
import org.jetbrains.android.facet.AndroidFacet;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Android-specific sync integration tests. */
@RunWith(JUnit4.class)
public class AndroidSyncTest extends BlazeSyncIntegrationTestCase {

  @Rule
  public final AndroidIntegrationTestSetupRule androidSetupRule =
      new AndroidIntegrationTestSetupRule();

  @Before
  public void setup() {
    mockSdk("android-25", "Android 25 SDK");
    registerProjectService(OCWorkspaceManagerAdapter.class, new MockOCWorkspaceManagerAdapter());
    registerApplicationService(
        CompilerVersionChecker.class, new MockCompilerVersionChecker("1234"));
  }

  private void mockSdk(String targetHash, String sdkName) {
    SdkTypeId sdkType = mock(SdkTypeId.class);
    when(sdkType.getName()).thenReturn("Android SDK");
    Sdk sdk = mock(Sdk.class);
    when(sdk.getName()).thenReturn(sdkName);
    when(sdk.getSdkType()).thenReturn(sdkType);
    MockBlazeSdkProvider sdkProvider = (MockBlazeSdkProvider) BlazeSdkProvider.getInstance();
    sdkProvider.addSdk(targetHash, sdk);
  }

  @Test
  public void testAndroidSyncAugmenterPresent() {
    assertThat(
            Arrays.stream(BlazeJavaSyncAugmenter.EP_NAME.getExtensions())
                .anyMatch(e -> e instanceof BlazeAndroidJavaSyncAugmenter))
        .isTrue();
  }

  @Test
  public void testSimpleSync() {
    setProjectView(
        "directories:",
        "  java/com/google",
        "targets:",
        "  //java/com/google:lib",
        "android_sdk_platform: android-25");

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
                .manifest("AndroidManifest.xml")
                .res("res/values/strings.xml")
                .res_java_package("com.google")
                .src("Source.java", "Other.java"));

    setTargetMap(targetMap);
    runBlazeSync(
        new BlazeSyncParams.Builder("Sync", SyncMode.INCREMENTAL)
            .addProjectViewTargets(true)
            .build());

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

  @Test
  public void testMultipleToolchainsNoIssue() {
    // Test what happens if there are multiple toolchains in the target map
    // (e.g., from --fat_apk_cpu)
    setProjectView(
        "directories:",
        "  java/com/google",
        "targets:",
        "  //java/com/google:app",
        "additional_languages:",
        "  c",
        "android_sdk_platform: android-25");
    workspace.createDirectory(new WorkspacePath("java/com/google"));
    workspace.createFile(
        new WorkspacePath("java/com/google/Source.java"),
        "package com.google;",
        "public class Source {}");

    workspace.createFile(new WorkspacePath("java/com/google/jni/native.cc"), "void foo() {}");
    workspace.createFile(new WorkspacePath("java/com/google/jni/native2.cc"), "void bar() {}");

    TargetMap targetMap =
        targetMap(
            cc_toolchain("//android_ndk_linux/toolchains:armv7a")
                .cc_target_name("arm-linux-androideabi")
                .cpp_executable("bin/arm-linux-androideabi-gcc")
                .base_compiler_options("-DOS_ANDROID", "-march=armv7-a")
                .built_in_include_dirs("lib/gcc/arm-linux-androideabi/4.8/include"),
            cc_toolchain("//android_ndk_linux/toolchains:aarch64")
                .cc_target_name("aarch64-linux-android")
                .cpp_executable("bin/aarch64-linux-android-gcc")
                .base_compiler_options("-DOS_ANDROID")
                .built_in_include_dirs("lib/gcc/aarch64-linux-android/4.9/include"),

            // Technically, blaze returns multiple instances of native libs (one for each CPU from
            // fat APK). However, we just pick the first instance we run into for the target map.
            // So it may be that we have:
            //   Main TC: only build target1
            //   Other TC2: build target1 + target2
            // After merging the target map it might look like target1 and target2 are built with
            // inconsistent TCs, even though it was originally consistent.
            cc_library("//java/com/google:native_lib2")
                .transitive_quote_include_dirs(".", "blaze-out/android-aarch64/genfiles")
                .transitive_system_include_dirs("third_party/java/jdk/include")
                .src("jni/native2.cc")
                .dep("//android_ndk_linux/toolchains:aarch64"),
            cc_library("//java/com/google:native_lib")
                .transitive_quote_include_dirs(".", "blaze-out/android-armv7a/genfiles")
                .transitive_system_include_dirs("third_party/java/jdk/include")
                .src("jni/native.cc")
                .dep("//java/com/google:native_lib2", "//android_ndk_linux/toolchains:armv7a"),
            android_binary("//java/com/google:app")
                .src("Source.java")
                .dep(
                    "//tools/jdk:toolchain",
                    "//android_ndk_linux/toolchains:armv7a",
                    "//java/com/google:native_lib",
                    "//java/com/google:native_lib2"));
    setTargetMap(targetMap);

    TargetIdeInfo targetWith32Dep =
        targetMap.get(TargetKey.forPlainTarget(Label.create("//java/com/google:native_lib")));
    TargetIdeInfo targetWith64Dep =
        targetMap.get(TargetKey.forPlainTarget(Label.create("//java/com/google:native_lib2")));
    CToolchainIdeInfo aarch32Toolchain =
        targetMap
            .get(TargetKey.forPlainTarget(Label.create("//android_ndk_linux/toolchains:armv7a")))
            .getcToolchainIdeInfo();
    CToolchainIdeInfo aarch64Toolchain =
        targetMap
            .get(TargetKey.forPlainTarget(Label.create("//android_ndk_linux/toolchains:aarch64")))
            .getcToolchainIdeInfo();

    // Try lightweight toolchain map build to check the mappings.
    ImmutableMap<TargetKey, CToolchainIdeInfo> toolchainMap =
        BlazeConfigurationToolchainResolver.buildToolchainLookupMap(new BlazeContext(), targetMap);
    assertThat(toolchainMap.get(targetWith32Dep.getKey())).isEqualTo(aarch32Toolchain);
    assertThat(toolchainMap.get(targetWith64Dep.getKey())).isEqualTo(aarch64Toolchain);

    // Now try the heavier-weight sync (which also does a toolchain map build).
    runBlazeSync(
        new BlazeSyncParams.Builder("Sync", SyncMode.INCREMENTAL)
            .addProjectViewTargets(true)
            .build());

    errorCollector.assertNoIssues();

    BlazeProjectData blazeProjectData =
        BlazeProjectDataManager.getInstance(getProject()).getBlazeProjectData();
    assertThat(blazeProjectData).isNotNull();
    assertThat(blazeProjectData.getTargetMap()).isEqualTo(targetMap);
    assertThat(blazeProjectData.getWorkspaceLanguageSettings().getWorkspaceType())
        .isEqualTo(WorkspaceType.ANDROID);
    assertThat(blazeProjectData.getWorkspaceLanguageSettings().isLanguageActive(LanguageClass.C))
        .isTrue();

    Module workspaceModule =
        ModuleFinder.getInstance(getProject())
            .findModuleByName(BlazeDataStorage.WORKSPACE_MODULE_NAME);
    assertThat(workspaceModule).isNotNull();
    assertThat(AndroidFacet.getInstance(workspaceModule)).isNotNull();
  }

  private class MockOCWorkspaceManagerAdapter extends OCWorkspaceManagerAdapter {
    @Override
    public OCWorkspace getWorkspace() {
      return BlazeCWorkspace.getInstance(getProject()).getWorkspace();
    }
  }
}
