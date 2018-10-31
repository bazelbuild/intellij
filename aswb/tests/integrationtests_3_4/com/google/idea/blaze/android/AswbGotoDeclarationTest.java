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
package com.google.idea.blaze.android;

import static com.google.common.truth.Truth.assertThat;
import static com.google.idea.blaze.android.targetmapbuilder.NbAndroidTarget.android_library;

import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.intellij.codeInsight.navigation.actions.GotoDeclarationAction;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import org.jetbrains.android.dom.wrappers.LazyValueResourceElementWrapper;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests that R class references are properly resolved by the android plugin. */
@RunWith(JUnit4.class)
public class AswbGotoDeclarationTest extends BlazeAndroidIntegrationTestCase {
  @Before
  public void setup() {
    setProjectView(
        "directories:",
        "  java/com/foo/gallery/activities",
        "targets:",
        "  //java/com/foo/gallery/activities:activities",
        "android_sdk_platform: android-27");
    mockSdk("android-27", "Android 27 SDK");
  }

  @Test
  public void gotoDeclaration_withExternalResources() {
    VirtualFile mainActivity =
        workspace.createFile(
            new WorkspacePath("java/com/foo/gallery/activities/MainActivity.java"),
            "package com.foo.gallery.activities",
            "import android.app.Activity;",
            "public class MainActivity extends Activity {",
            "  public void referenceResources() {",
            "    System.out.println(R.style.Base_Highlight); // External resource",
            "  }",
            "}");

    VirtualFile stylesXml =
        workspace.createFile(
            new WorkspacePath("java/com/foo/libs/res/values/styles.xml"),
            "<?xml version=\"1.0\" encoding=\"utf-8\"?>",
            "<resources>",
            "    <style name=\"Base.Highlight\" parent=\"android:Theme.DeviceDefault\">",
            "        <item name=\"android:textSize\">30dp</item>",
            "        <item name=\"android:textColor\">#FF0000</item>",
            "        <item name=\"android:textStyle\">bold</item>",
            "    </style>",
            "    <style name=\"Base.Normal\" parent=\"android:Theme.DeviceDefault\">",
            "        <item name=\"android:textSize\">15dp</item>",
            "        <item name=\"android:textColor\">#C0C0C0</item>",
            "    </style>",
            "</resources>");

    setTargetMap(
        android_library("//java/com/foo/gallery/activities:activities")
            .src("MainActivity.java")
            .dep("//java/com/foo/libs:libs")
            .res_java_package("com.foo.gallery.activities")
            .res("res"),
        android_library("//java/com/foo/libs:libs").res_java_package("com.foo.libs").res("res"));
    runFullBlazeSync();

    testFixture.configureFromExistingVirtualFile(mainActivity);
    int referenceIndex =
        testFixture.getEditor().getDocument().getText().indexOf("R.style.Base_Highlight");

    PsiElement foundElement =
        GotoDeclarationAction.findTargetElement(
            getProject(), testFixture.getEditor(), referenceIndex);
    foundElement = LazyValueResourceElementWrapper.computeLazyElement(foundElement);

    assertThat(foundElement).isNotNull();
    assertThat(foundElement.getContainingFile()).isNotNull();
    assertThat(foundElement.getContainingFile().getVirtualFile()).isEqualTo(stylesXml);
  }

  @Test
  public void gotoDeclaration_withLocalResources() {
    VirtualFile mainActivity =
        workspace.createFile(
            new WorkspacePath("java/com/foo/gallery/activities/MainActivity.java"),
            "package com.foo.gallery.activities",
            "import android.app.Activity;",
            "public class MainActivity extends Activity {",
            "  public void referenceResources() {",
            "    System.out.println(R.menu.settings); // Local resource",
            "  }",
            "}");

    VirtualFile settingsXml =
        workspace.createFile(
            new WorkspacePath("java/com/foo/gallery/activities/res/menu/settings.xml"),
            "<?xml version=\"1.0\" encoding=\"utf-8\"?>",
            "<menu xmlns:tools=\"http://schemas.android.com/tools\"",
            "    xmlns:android=\"http://schemas.android.com/apk/res/android\">",
            "    <item",
            "        android:id=\"@+id/action_settings\"",
            "        android:orderInCategory=\"1\"",
            "        android:title=\"@string/settings_title\"/>",
            "</menu>");

    setTargetMap(
        android_library("//java/com/foo/gallery/activities:activities")
            .src("MainActivity.java")
            .res_java_package("com.foo.gallery.activities")
            .res("res"));
    runFullBlazeSync();

    testFixture.configureFromExistingVirtualFile(mainActivity);
    int referenceIndex = testFixture.getEditor().getDocument().getText().indexOf("R.menu.settings");

    PsiElement foundElement =
        GotoDeclarationAction.findTargetElement(
            getProject(), testFixture.getEditor(), referenceIndex);

    assertThat(foundElement).isNotNull();
    assertThat(foundElement.getContainingFile()).isNotNull();
    assertThat(foundElement.getContainingFile().getVirtualFile()).isEqualTo(settingsXml);
  }
}
