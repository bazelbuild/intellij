/*
 * Copyright (C) 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.android.functional;

import static com.google.common.truth.Truth.assertThat;
import static com.google.idea.blaze.android.targetmapbuilder.NbAndroidTarget.android_binary;
import static com.google.idea.blaze.android.targetmapbuilder.NbAndroidTarget.android_library;

import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.editors.theme.ThemeResolver;
import com.android.tools.idea.editors.theme.datamodels.ConfiguredThemeEditorStyle;
import com.android.tools.idea.rendering.AswbRenderTestUtils;
import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.android.BlazeAndroidIntegrationTestCase;
import com.google.idea.blaze.android.MockSdkUtil;
import com.google.idea.blaze.android.libraries.AarLibraryFileBuilder;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.sync.BlazeBuildParams;
import com.google.idea.blaze.base.sync.BlazeSyncParams;
import com.google.idea.blaze.base.sync.SyncMode;
import com.google.idea.blaze.base.sync.projectstructure.ModuleFinder;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFile;
import org.intellij.lang.annotations.Language;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Integration test for the correct population of themes after a sync. */
@RunWith(JUnit4.class)
public class AswbThemePopulationTest extends BlazeAndroidIntegrationTestCase {
  @Language("XML")
  private static final String LAYOUT_XML =
      "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
          + "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
          + "              android:layout_width=\"match_parent\"\n"
          + "              android:layout_height=\"match_parent\">\n"
          + "</LinearLayout>";

  @Language("XML")
  private static final String LOCAL_STYLES_XML =
      "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
          + "<resources>\n"
          + "  <style name=\"local_text_theme\" parent=\"android:Theme.DeviceDefault\">\n"
          + "    <item name=\"android:textColor\">#00FF00</item>\n"
          + "  </style>\n"
          + "</resources>\n";

  @Language("XML")
  private static final String EXTERNAL_STYLES_XML =
      "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
          + "<resources>\n"
          + "  <style name=\"external_text_theme\" parent=\"android:Theme.DeviceDefault\">\n"
          + "    <item name=\"android:textSize\">30dp</item>\n"
          + "</style>\n"
          + "</resources>";

  @Before
  public void setUpRenderTaskTest() throws Exception {
    AswbRenderTestUtils.beforeRenderTestCase();
  }

  @After
  public void tearDownRenderTaskTest() throws Exception {
    AswbRenderTestUtils.afterRenderTestCase();
  }

  @Test
  public void themeResolution_withLocalTheme_populatesLocalTheme() {
    MockSdkUtil.registerSdk(workspace, "25");
    VirtualFile layoutXml =
        workspace.createFile(
            new WorkspacePath("java/com/google/res/layout/activity_main.xml"), LAYOUT_XML);
    workspace.createFile(
        new WorkspacePath("java/com/google/res/values/styles.xml"), LOCAL_STYLES_XML);
    setTargetMap(android_binary("//java/com/google:app").java_toolchain_version("8").res("res"));
    setProjectView(
        "directories:",
        "  java/com/google",
        "targets:",
        "  //java/com/google:app",
        "android_sdk_platform: android-25");

    runBlazeSync(
        BlazeSyncParams.builder()
            .setTitle("Sync")
            .setSyncMode(SyncMode.INCREMENTAL)
            .setSyncOrigin("test")
            .setBlazeBuildParams(BlazeBuildParams.fromProject(getProject()))
            .setAddProjectViewTargets(true)
            .build());
    errorCollector.assertNoIssues();

    Module resourceModule =
        ModuleFinder.getInstance(getProject()).findModuleByName("java.com.google.app");
    Configuration configuration = AswbRenderTestUtils.getConfiguration(resourceModule, layoutXml);
    ImmutableList<ConfiguredThemeEditorStyle> localThemes =
        new ThemeResolver(configuration).getLocalThemes();
    assertThat(localThemes).hasSize(1);
    assertThat(localThemes.get(0).getName()).isEqualTo("local_text_theme");
  }

  @Test
  public void themeResolution_withThemeFromExternalLibrary_populatesExternalTheme() {
    MockSdkUtil.registerSdk(workspace, "25");
    VirtualFile layoutXml =
        workspace.createFile(
            new WorkspacePath("java/com/google/res/layout/activity_main.xml"), LAYOUT_XML);
    // External libraries are exposed as AARs that are unpacked post-sync.  Instead of creating a
    // normal source file, we need to add it to an AAR and then expose it through the target map
    // as the resource folder of the android_library external library target.  When
    // go-to-declaration
    // is invoked on elements declared in the AAR, the IDE should open the resource file inside the
    // unpacked AAR.
    AarLibraryFileBuilder.aar(workspaceRoot, "java/com/foo/libs/libs_aar.aar")
        .src("res/values/styles.xml", ImmutableList.of(EXTERNAL_STYLES_XML))
        .build();
    setTargetMap(
        android_binary("//java/com/google:app")
            .java_toolchain_version("8")
            .dep("//java/com/foo/libs:libs")
            .res("res"),
        android_library("//java/com/foo/libs:libs")
            .res_folder("//java/com/foo/libs/res", "libs_aar.aar"));
    setProjectView(
        "directories:",
        "  java/com/google",
        "targets:",
        "  //java/com/google:app",
        "android_sdk_platform: android-25");

    runBlazeSync(
        BlazeSyncParams.builder()
            .setTitle("Sync")
            .setSyncMode(SyncMode.INCREMENTAL)
            .setSyncOrigin("test")
            .setBlazeBuildParams(BlazeBuildParams.fromProject(getProject()))
            .setAddProjectViewTargets(true)
            .build());
    errorCollector.assertNoIssues();

    Module resourceModule =
        ModuleFinder.getInstance(getProject()).findModuleByName("java.com.google.app");
    Configuration configuration = AswbRenderTestUtils.getConfiguration(resourceModule, layoutXml);
    ImmutableList<ConfiguredThemeEditorStyle> externalThemes =
        new ThemeResolver(configuration).getExternalLibraryThemes();
    assertThat(externalThemes).hasSize(1);
    assertThat(externalThemes.get(0).getName()).isEqualTo("external_text_theme");
  }

  @Test
  public void themeResolution_withNewlyAddedThemes_populatesThemesAfterSecondSync() {
    MockSdkUtil.registerSdk(workspace, "25");
    setProjectView(
        "directories:",
        "  java/com/google",
        "targets:",
        "  //java/com/google:app",
        "android_sdk_platform: android-25");
    setTargetMap(android_binary("//java/com/google:app").java_toolchain_version("8").res("res"));
    VirtualFile layoutXml =
        workspace.createFile(
            new WorkspacePath("java/com/google/res/layout/activity_main.xml"), LAYOUT_XML);

    // First sync. This time with no themes defined in resources.
    runBlazeSync(
        BlazeSyncParams.builder()
            .setTitle("Sync")
            .setSyncMode(SyncMode.INCREMENTAL)
            .setSyncOrigin("test")
            .setBlazeBuildParams(BlazeBuildParams.fromProject(getProject()))
            .setAddProjectViewTargets(true)
            .build());
    errorCollector.assertNoIssues();

    Module resourceModule =
        ModuleFinder.getInstance(getProject()).findModuleByName("java.com.google.app");
    Configuration configuration = AswbRenderTestUtils.getConfiguration(resourceModule, layoutXml);
    ThemeResolver themeResolver = new ThemeResolver(configuration);
    assertThat(themeResolver.getLocalThemes()).hasSize(0);
    assertThat(themeResolver.getExternalLibraryThemes()).hasSize(0);

    // Add in an additional target that includes another theme
    setProjectView(
        "directories:",
        "  java/com/google",
        "  java/com/other",
        "targets:",
        "  //java/com/google:app",
        "  //java/com/other:app",
        "android_sdk_platform: android-25");
    setTargetMap(
        android_binary("//java/com/google:app").java_toolchain_version("8").res("res"),
        android_binary("//java/com/other:app").java_toolchain_version("8").res("res"));

    // Second sync. This time with the theme.
    workspace.createFile(
        new WorkspacePath("java/com/other/res/values/styles.xml"), LOCAL_STYLES_XML);

    runBlazeSync(
        BlazeSyncParams.builder()
            .setTitle("Sync")
            .setSyncMode(SyncMode.INCREMENTAL)
            .setSyncOrigin("test")
            .setBlazeBuildParams(BlazeBuildParams.fromProject(getProject()))
            .setAddProjectViewTargets(true)
            .build());
    errorCollector.assertNoIssues();

    Module otherResourceModule =
        ModuleFinder.getInstance(getProject()).findModuleByName("java.com.other.app");
    Configuration otherConfig =
        AswbRenderTestUtils.getConfiguration(otherResourceModule, layoutXml);
    ImmutableList<ConfiguredThemeEditorStyle> themes =
        new ThemeResolver(otherConfig).getLocalThemes();
    assertThat(themes).hasSize(1);
    assertThat(themes.get(0).getName()).isEqualTo("local_text_theme");
  }
}
