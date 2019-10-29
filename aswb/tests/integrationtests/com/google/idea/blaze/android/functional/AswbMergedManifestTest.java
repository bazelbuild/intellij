/*
 * Copyright (C) 2019 The Android Open Source Project
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

import com.android.tools.idea.model.MergedManifestManager;
import com.android.tools.lint.checks.PermissionHolder;
import com.google.idea.blaze.android.BlazeAndroidIntegrationTestCase;
import com.google.idea.blaze.android.MockSdkUtil;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.IdeaSourceProvider;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests that merged manifest calculations are accurate for Blaze Android projects. */
@RunWith(JUnit4.class)
public class AswbMergedManifestTest extends BlazeAndroidIntegrationTestCase {
  @Before
  public void setup() {
    setProjectView(
        "directories:",
        "  java/com/example",
        "targets:",
        "  //java/com/example/...:all",
        "android_sdk_platform: android-27");
    MockSdkUtil.registerSdk(workspace, "27");
  }

  @Test
  public void manifestBelongsToResourceModule() {
    VirtualFile manifest =
        workspace.createFile(new WorkspacePath("java/com/example/target/AndroidManifest.xml"));
    setTargetMap(
        android_binary("//java/com/example/target:target")
            .manifest("//java/com/example/target/AndroidManifest.xml")
            .res("res"));
    runFullBlazeSync();

    Module targetResourceModule =
        ModuleManager.getInstance(getProject()).findModuleByName("java.com.example.target.target");
    assertThat(targetResourceModule).isNotNull();
    AndroidFacet targetFacet = AndroidFacet.getInstance(targetResourceModule);
    assertThat(targetFacet).isNotNull();

    // Verify the mapping from resource module to manifest.
    assertThat(IdeaSourceProvider.getManifestFiles(targetFacet)).containsExactly(manifest);
    // Verify the mapping from manifest back to resource module.
    assertThat(AndroidFacet.getInstance(manifest, getProject())).isEqualTo(targetFacet);
  }

  /**
   * Creates an Android manifest file for the given package that uses the given permission.
   *
   * @return the label of the newly-created manifest
   */
  private String createManifestWithPermission(String packageName, String permissionName) {
    String relativePath = "java/" + packageName.replace(".", "/") + "/AndroidManifest.xml";
    workspace.createFile(
        new WorkspacePath(relativePath),
        "<manifest xmlns:android='http://schemas.android.com/apk/res/android'",
        "    package='" + packageName + "'>",
        "    <uses-permission",
        "        android:name=\"" + permissionName + "\" />",
        "</manifest>");
    return "//" + relativePath;
  }

  @Test
  public void excludesManifestsFromUnrelatedModules() {
    setTargetMap(
        android_binary("//java/com/example/target:target")
            .manifest(
                createManifestWithPermission("com.example.target", "android.permission.BLUETOOTH"))
            .res("res")
            .dep("//java/com/example/direct:direct"),
        android_library("//java/com/example/direct:direct")
            .manifest(
                createManifestWithPermission("com.example.direct", "android.permission.SEND_SMS"))
            .res("res")
            .dep("//java/com/example/transitive:transitive"),
        android_library("//java/com/example/transitive:transitive")
            .manifest(
                createManifestWithPermission(
                    "com.example.transitive", "android.permission.INTERNET"))
            .res("res"),
        android_library("//java/com/example/irrelevant:irrelevant")
            .manifest(
                createManifestWithPermission(
                    "com.example.irrelevant", "android.permission.WRITE_EXTERNAL_STORAGE"))
            .res("res"));
    runFullBlazeSync();

    Module targetResourceModule =
        ModuleManager.getInstance(getProject()).findModuleByName("java.com.example.target.target");
    assertThat(targetResourceModule).isNotNull();
    PermissionHolder permissions =
        MergedManifestManager.getSnapshot(targetResourceModule).getPermissionHolder();

    // We should have all the permissions used by the binary and its transitive dependencies...
    assertThat(permissions.hasPermission("android.permission.BLUETOOTH")).isTrue();
    assertThat(permissions.hasPermission("android.permission.SEND_SMS")).isTrue();
    assertThat(permissions.hasPermission("android.permission.INTERNET")).isTrue();
    // ... but nothing from libraries that the binary doesn't depend on
    assertThat(permissions.hasPermission("android.permission.WRITE_EXTERNAL_STORAGE")).isFalse();
  }
}
