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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.android.AndroidIntegrationTestSetupRule;
import com.google.idea.blaze.android.sdk.BlazeSdkProvider;
import com.google.idea.blaze.android.sdk.MockBlazeSdkProvider;
import com.google.idea.blaze.base.ideinfo.AndroidIdeInfo;
import com.google.idea.blaze.base.ideinfo.CIdeInfo;
import com.google.idea.blaze.base.ideinfo.CToolchainIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetMap;
import com.google.idea.blaze.base.ideinfo.TargetMapBuilder;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.ExecutionRootPath;
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.model.primitives.WorkspaceType;
import com.google.idea.blaze.base.sync.BlazeSyncIntegrationTestCase;
import com.google.idea.blaze.base.sync.BlazeSyncParams;
import com.google.idea.blaze.base.sync.BlazeSyncParams.SyncMode;
import com.google.idea.blaze.base.sync.data.BlazeDataStorage;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.base.sync.projectstructure.ModuleFinder;
import com.google.idea.blaze.cpp.BlazeCWorkspace;
import com.google.idea.blaze.java.sync.BlazeJavaSyncAugmenter;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkTypeId;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.cidr.lang.OCLanguageKind;
import com.jetbrains.cidr.lang.workspace.OCResolveConfiguration;
import com.jetbrains.cidr.lang.workspace.OCWorkspace;
import com.jetbrains.cidr.lang.workspace.OCWorkspaceManager;
import com.jetbrains.cidr.lang.workspace.compiler.OCCompilerSettings;
import java.util.Arrays;
import java.util.List;
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
    registerProjectService(OCWorkspaceManager.class, new MockOCWorkspaceManager());
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
  public void testSimpleSync() throws Exception {
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
        TargetMapBuilder.builder()
            .addTarget(
                TargetIdeInfo.builder()
                    .setBuildFile(sourceRoot("java/com/google/BUILD"))
                    .setLabel("//java/com/google:lib")
                    .setKind("android_library")
                    .setAndroidInfo(
                        AndroidIdeInfo.builder()
                            .setManifestFile(sourceRoot("java/com/google/AndroidManifest.xml"))
                            .addResource(sourceRoot("java/com/google/res/values/strings.xml"))
                            .setResourceJavaPackage("com.google")
                            .setGenerateResourceClass(true))
                    .addSource(sourceRoot("java/com/google/Source.java"))
                    .addSource(sourceRoot("java/com/google/Other.java")))
            .build();

    setTargetMap(targetMap);

    runBlazeSync(
        new BlazeSyncParams.Builder("Sync", SyncMode.INCREMENTAL)
            .addProjectViewTargets(true)
            .build());

    errorCollector.assertNoIssues();

    BlazeProjectData blazeProjectData =
        BlazeProjectDataManager.getInstance(getProject()).getBlazeProjectData();
    assertThat(blazeProjectData).isNotNull();
    assertThat(blazeProjectData.targetMap).isEqualTo(targetMap);
    assertThat(blazeProjectData.workspaceLanguageSettings.getWorkspaceType())
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

    workspace.createFile(
        new WorkspacePath("java/com/google/Other.java"),
        "package com.google;",
        "public class Other {}");

    workspace.createFile(new WorkspacePath("java/com/google/jni/native.cc"), "void foo() {}");
    workspace.createFile(new WorkspacePath("java/com/google/jni/native2.cc"), "void bar() {}");

    TargetMap targetMap =
        TargetMapBuilder.builder()
            .addTarget(
                TargetIdeInfo.builder()
                    .setBuildFile(sourceRoot("android_ndk_linux/toolchains/BUILD"))
                    .setLabel("//android_ndk_linux/toolchains:armv7a")
                    .setKind(Kind.CC_TOOLCHAIN)
                    .setCToolchainInfo(
                        CToolchainIdeInfo.builder()
                            .setTargetName("arm-linux-androideabi")
                            .setCppExecutable(
                                new ExecutionRootPath("bin/arm-linux-androideabi-gcc"))
                            .setPreprocessorExecutable(
                                new ExecutionRootPath("bin/arm-linux-androideabi-cpp"))
                            .addBaseCompilerOptions(
                                ImmutableList.of(
                                    "-DOS_ANDROID",
                                    "-mbionic",
                                    "-ffunction-sections",
                                    "-march=armv7-a",
                                    "-mfpu=vfpv3-d16"))
                            .addCppCompilerOptions(ImmutableList.of("-std=gnu++11"))
                            .addBuiltInIncludeDirectories(
                                ImmutableList.of(
                                    new ExecutionRootPath(
                                        "lib/gcc/arm-linux-androideabi/4.8/include")))
                            .addLinkOptions(
                                ImmutableList.of(
                                    "--sysroot=android_ndk_linux/platforms/android-18/arch-arm"))
                            .addUnfilteredCompilerOptions(
                                ImmutableList.of(
                                    "--sysroot=android_ndk_linux/platforms/android-18/arch-arm"))
                            .addUnfilteredToolchainSystemIncludes(
                                ImmutableList.of(
                                    new ExecutionRootPath(
                                        "android_ndk_linux/sources/llvm-libc++/libcxx/include")))))
            .addTarget(
                TargetIdeInfo.builder()
                    .setBuildFile(sourceRoot("android_ndk_linux/toolchains/BUILD"))
                    .setLabel("//android_ndk_linux/toolchains:aarch64")
                    .setKind(Kind.CC_TOOLCHAIN)
                    .setCToolchainInfo(
                        CToolchainIdeInfo.builder()
                            .setTargetName("aarch64-linux-android")
                            .setCppExecutable(
                                new ExecutionRootPath("prebuilt/bin/aarch64-linux-android-gcc"))
                            .setPreprocessorExecutable(
                                new ExecutionRootPath("prebuilt/bin/aarch64-linux-android-cpp"))
                            .addBaseCompilerOptions(
                                ImmutableList.of("-DOS_ANDROID", "-mbionic", "-ffunction-sections"))
                            .addCppCompilerOptions(ImmutableList.of("-std=gnu++11"))
                            .addBuiltInIncludeDirectories(
                                ImmutableList.of(
                                    new ExecutionRootPath(
                                        "lib/gcc/aarch64-linux-android/4.9/include")))
                            .addLinkOptions(
                                ImmutableList.of(
                                    "--sysroot=android_ndk_linux/platforms/android-21/arch-arm64"))
                            .addUnfilteredCompilerOptions(
                                ImmutableList.of(
                                    "--sysroot=android_ndk_linux/platforms/android-21/arch-arm64"))
                            .addUnfilteredToolchainSystemIncludes(
                                ImmutableList.of(
                                    new ExecutionRootPath(
                                        "android_ndk_linux/sources/llvm-libc++/libcxx/include")))))
            .addTarget(
                TargetIdeInfo.builder()
                    .setBuildFile(sourceRoot("java/com/google/BUILD"))
                    .setLabel("//java/com/google:lib")
                    .setKind("android_library")
                    .setAndroidInfo(
                        AndroidIdeInfo.builder()
                            .setManifestFile(sourceRoot("java/com/google/AndroidManifest.xml"))
                            .addResource(sourceRoot("java/com/google/res/values/strings.xml"))
                            .setResourceJavaPackage("com.google")
                            .setGenerateResourceClass(true))
                    .addSource(sourceRoot("java/com/google/Other.java")))
            // Technically, blaze returns multiple instances of native libs (one for each CPU from
            // fat APK). However, we just pick the first instance we run into for the target map.
            .addTarget(
                TargetIdeInfo.builder()
                    .setBuildFile(sourceRoot("java/com/google/BUILD"))
                    .setLabel("//java/com/google:native_lib")
                    .setKind("cc_library")
                    .setCInfo(
                        CIdeInfo.builder()
                            .addTransitiveQuoteIncludeDirectories(
                                ImmutableList.of(
                                    new ExecutionRootPath("."),
                                    new ExecutionRootPath("blaze-out/android-aarch64-etc/genfiles"),
                                    new ExecutionRootPath(
                                        "blaze-out/android-aarch64-etc/genfiles/third_party/java")))
                            .addTransitiveSystemIncludeDirectories(
                                ImmutableList.of(
                                    new ExecutionRootPath("third_party/stl/gcc3"),
                                    new ExecutionRootPath("third_party/java/jdk/include"))))
                    .addSource(sourceRoot("java/com/google/jni/native.cc"))
                    .addDependency("//android_ndk_linux/toolchains:aarch64"))
            .addTarget(
                TargetIdeInfo.builder()
                    .setBuildFile(sourceRoot("java/com/google/BUILD"))
                    .setLabel("//java/com/google:native_lib2")
                    .setKind("cc_library")
                    .setCInfo(
                        CIdeInfo.builder()
                            .addTransitiveQuoteIncludeDirectories(
                                ImmutableList.of(
                                    new ExecutionRootPath("."),
                                    new ExecutionRootPath("blaze-out/android-aarch64-etc/genfiles"),
                                    new ExecutionRootPath(
                                        "blaze-out/android-aarch64-etc/genfiles/third_party/java")))
                            .addTransitiveSystemIncludeDirectories(
                                ImmutableList.of(
                                    new ExecutionRootPath("third_party/stl/gcc3"),
                                    new ExecutionRootPath("third_party/java/jdk/include"))))
                    .addSource(sourceRoot("java/com/google/jni/native2.cc"))
                    .addDependency("//java/com/google:native_lib")
                    .addDependency("//android_ndk_linux/toolchains:armv7a"))
            // Other targets like android_binary and android_test might also depend on
            // a cc_toolchain.
            .addTarget(
                TargetIdeInfo.builder()
                    .setBuildFile(sourceRoot("java/com/google/BUILD"))
                    .setLabel("//java/com/google:app")
                    .setKind("android_binary")
                    .setAndroidInfo(
                        AndroidIdeInfo.builder()
                            .setManifestFile(sourceRoot("java/com/google/AndroidManifest.xml"))
                            .setResourceJavaPackage("com.google")
                            .setGenerateResourceClass(true))
                    .addSource(sourceRoot("java/com/google/Source.java"))
                    .addDependency("//tools/jdk:toolchain")
                    .addDependency("//android_ndk_linux/toolchains:armv7a")
                    .addDependency("//java/com/google:lib")
                    .addDependency("//java/com/google:native_lib")
                    .addDependency("//java/com/google:native_lib2"))
            .build();

    setTargetMap(targetMap);

    runBlazeSync(
        new BlazeSyncParams.Builder("Sync", SyncMode.INCREMENTAL)
            .addProjectViewTargets(true)
            .build());

    errorCollector.assertNoIssues();

    BlazeProjectData blazeProjectData =
        BlazeProjectDataManager.getInstance(getProject()).getBlazeProjectData();
    assertThat(blazeProjectData).isNotNull();
    assertThat(blazeProjectData.targetMap).isEqualTo(targetMap);
    assertThat(blazeProjectData.workspaceLanguageSettings.getWorkspaceType())
        .isEqualTo(WorkspaceType.ANDROID);
    assertThat(blazeProjectData.workspaceLanguageSettings.isLanguageActive(LanguageClass.C))
        .isTrue();

    // Check that the workspace is set to android
    Module workspaceModule =
        ModuleFinder.getInstance(getProject())
            .findModuleByName(BlazeDataStorage.WORKSPACE_MODULE_NAME);
    assertThat(workspaceModule).isNotNull();
    assertThat(AndroidFacet.getInstance(workspaceModule)).isNotNull();

    // Check resolve configurations for the native code match the toolchain that was in
    // the library's deps (not switched for some reason).
    VirtualFile nativeCc =
        fileSystem.findFile(
            workspaceRoot
                .fileForPath(new WorkspacePath("java/com/google/jni/native.cc"))
                .getPath());
    VirtualFile nativeCc2 =
        fileSystem.findFile(
            workspaceRoot
                .fileForPath(new WorkspacePath("java/com/google/jni/native2.cc"))
                .getPath());

    List<? extends OCResolveConfiguration> resolveConfigurations =
        OCWorkspaceManager.getWorkspace(getProject()).getConfigurationsForFile(nativeCc);
    assertThat(resolveConfigurations).hasSize(1);
    OCCompilerSettings compilerSettings = resolveConfigurations.get(0).getCompilerSettings();
    List<String> compilerSwitches =
        compilerSettings.getCompilerSwitches(OCLanguageKind.CPP, nativeCc).getCommandLineArgs();
    assertThat(compilerSwitches)
        .contains("--sysroot=android_ndk_linux/platforms/android-21/arch-arm64");

    resolveConfigurations =
        OCWorkspaceManager.getWorkspace(getProject()).getConfigurationsForFile(nativeCc2);
    assertThat(resolveConfigurations).hasSize(1);
    compilerSettings = resolveConfigurations.get(0).getCompilerSettings();
    compilerSwitches =
        compilerSettings.getCompilerSwitches(OCLanguageKind.CPP, nativeCc).getCommandLineArgs();
    assertThat(compilerSwitches)
        .contains("--sysroot=android_ndk_linux/platforms/android-18/arch-arm");
  }

  private class MockOCWorkspaceManager extends OCWorkspaceManager {

    @Override
    public OCWorkspace getWorkspace() {
      return BlazeCWorkspace.getInstance(getProject());
    }
  }
}
