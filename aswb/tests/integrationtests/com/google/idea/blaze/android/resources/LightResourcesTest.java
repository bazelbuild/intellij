/*
 * Copyright 2020 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.android.resources;

import static com.google.common.truth.Truth.assertThat;
import static com.google.idea.blaze.android.targetmapbuilder.NbAndroidTarget.android_library;

import com.android.tools.idea.res.AarResourceRepositoryCache;
import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.android.BlazeAndroidIntegrationTestCase;
import com.google.idea.blaze.android.libraries.AarLibraryFileBuilder;
import com.google.idea.blaze.android.sdk.AndroidSdkProviderForTests;
import com.google.idea.blaze.android.sdk.BlazeSdkProvider;
import com.google.idea.blaze.android.targetmapbuilder.NbAndroidTarget;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.common.experiments.ExperimentService;
import com.google.idea.common.experiments.MockExperimentService;
import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.light.LightElement;
import com.intellij.testFramework.EditorTestUtil;
import java.util.List;
import java.util.Objects;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link BlazeLightResourceClassService} */
@RunWith(JUnit4.class)
public final class LightResourcesTest extends BlazeAndroidIntegrationTestCase {

  private static final String CARET = EditorTestUtil.CARET_TAG;

  @Before
  public void setup() {
    setProjectView(
        "directories:",
        "  java/com/foo/bar",
        "targets:",
        "  //java/com/foo/bar:bar",
        "android_sdk_platform: " + AndroidSdkProviderForTests.SUPPORTED_SDK);
    AndroidSdkProviderForTests sdkProvider = new AndroidSdkProviderForTests();
    registerApplicationService(BlazeSdkProvider.class, sdkProvider);

    MockExperimentService experimentService = new MockExperimentService();
    experimentService.setFeatureRolloutExperiment(
        BlazeLightResourceClassService.workspaceResourcesFeature, 100);
    registerApplicationComponent(ExperimentService.class, experimentService);
  }

  @After
  public void clearAarCache() {
    AarResourceRepositoryCache.getInstance().clear();
  }

  @Test
  public void testSameTargetResource_testHighlighting_noErrors() {
    VirtualFile mainActivityFile =
        workspace.createFile(
            new WorkspacePath("java/com/foo/bar/MainActivity.java"),
            "package com.foo.bar;",
            "",
            "import android.app.Activity;",
            "import android.os.Bundle;",
            "",
            "public class MainActivity extends Activity {",
            "  @Override",
            "  protected void onCreate(Bundle savedInstanceState) {",
            "    super.onCreate(savedInstanceState);",
            "    getResources().getString(R.string.app" + CARET + "String);",
            "  }",
            "}");

    workspace.createFile(
        new WorkspacePath("java/com/foo/bar/res/values/strings.xml"),
        "<resources>",
        "    <string name=\"appString\">Hello from app</string>",
        "</resources>");

    setTargetMap(android_library("//java/com/foo/bar:bar").src("MainActivity.java").res("res"));
    runFullBlazeSync();
    testFixture.configureFromExistingVirtualFile(mainActivityFile);
    testFixture.checkHighlighting();
  }

  @Test
  public void testSameTargetMissingResource_testHighlighting_hasErrors() {
    VirtualFile mainActivityFile =
        workspace.createFile(
            new WorkspacePath("java/com/foo/bar/MainActivity.java"),
            "package com.foo.bar;",
            "",
            "import android.app.Activity;",
            "import android.os.Bundle;",
            "",
            "public class MainActivity extends Activity {",
            "  @Override",
            "  protected void onCreate(Bundle savedInstanceState) {",
            "    super.onCreate(savedInstanceState);",
            "    getResources().getString(R.string.nonexistentString);",
            "  }",
            "}");

    workspace.createFile(
        new WorkspacePath("java/com/foo/bar/res/values/strings.xml"),
        "<resources>",
        "    <string name=\"appString\">Hello from app</string>",
        "</resources>");

    setTargetMap(android_library("//java/com/foo/bar:bar").src("MainActivity.java").res("res"));
    runFullBlazeSync();
    testFixture.configureFromExistingVirtualFile(mainActivityFile);
    List<HighlightInfo> highlightInfos = testFixture.doHighlighting(HighlightSeverity.ERROR);
    assertThat(highlightInfos).hasSize(1);
    assertThat(highlightInfos.get(0).type).isEqualTo(HighlightInfoType.WRONG_REF);
    assertThat(highlightInfos.get(0).getText()).isEqualTo("nonexistentString");
  }

  @Test
  public void testDependentTargetResource_testHighlighting_noErrors() {
    // Setup source file
    VirtualFile mainActivityFile =
        workspace.createFile(
            new WorkspacePath("java/com/foo/bar/MainActivity.java"),
            "package com.foo.bar;",
            "",
            "import android.app.Activity;",
            "import android.os.Bundle;",
            "",
            "public class MainActivity extends Activity {",
            "  @Override",
            "  protected void onCreate(Bundle savedInstanceState) {",
            "    super.onCreate(savedInstanceState);",
            "    getResources().getString(R.string.appString);",
            "  }",
            "}");

    // Source target creates a module for its resources and links to the aar exported by
    // //java/com/foo/baz:baz
    NbAndroidTarget sourceTarget =
        android_library("//java/com/foo/bar:bar")
            .src("MainActivity.java")
            .res("res")
            .manifest_value("package", "com.foo.bar")
            .dep("//java/com/foo/baz:baz");

    String stringXml =
        String.join(
            "\n",
            "<resources>",
            "    <string name=\"appString\">Hello from app</string>",
            "</resources>");
    workspace.createFile(new WorkspacePath("java/com/foo/baz/res/values/strings.xml"), stringXml);
    NbAndroidTarget depTarget =
        android_library("//java/com/foo/baz:baz")
            .res_folder("res", "lib_aar.aar")
            .manifest_value("package", "com.foo.baz");

    AarLibraryFileBuilder.aar(workspaceRoot, depTarget.getAarList().get(0).getRelativePath())
        .src("res/values/strings.xml", ImmutableList.of(stringXml))
        .build();

    setTargetMap(sourceTarget, depTarget);
    runFullBlazeSync();
    testFixture.configureFromExistingVirtualFile(mainActivityFile);
    testFixture.checkHighlighting();
  }

  @Test
  public void testDependentTargetMissingResource_testHighlighting_hasErrors() {
    // Setup source file
    VirtualFile mainActivityFile =
        workspace.createFile(
            new WorkspacePath("java/com/foo/bar/MainActivity.java"),
            "package com.foo.bar;",
            "",
            "import android.app.Activity;",
            "import android.os.Bundle;",
            "",
            "public class MainActivity extends Activity {",
            "  @Override",
            "  protected void onCreate(Bundle savedInstanceState) {",
            "    super.onCreate(savedInstanceState);",
            "    getResources().getString(R.string.nonexistentString);",
            "  }",
            "}");

    // Source target creates a module for its resources and links to the aar exported by
    // //java/com/foo/baz:baz
    NbAndroidTarget sourceTarget =
        android_library("//java/com/foo/bar:bar")
            .src("MainActivity.java")
            .res("res")
            .manifest_value("package", "com.foo.bar")
            .dep("//java/com/foo/baz:baz");

    String stringXml =
        String.join(
            "\n",
            "<resources>",
            "    <string name=\"appString\">Hello from app</string>",
            "</resources>");
    workspace.createFile(new WorkspacePath("java/com/foo/baz/res/values/strings.xml"), stringXml);
    NbAndroidTarget depTarget =
        android_library("//java/com/foo/baz:baz")
            .res_folder("res", "lib_aar.aar")
            .manifest_value("package", "com.foo.baz");

    AarLibraryFileBuilder.aar(workspaceRoot, depTarget.getAarList().get(0).getRelativePath())
        .src("res/values/strings.xml", ImmutableList.of(stringXml))
        .build();

    setTargetMap(sourceTarget, depTarget);
    runFullBlazeSync();
    testFixture.configureFromExistingVirtualFile(mainActivityFile);
    List<HighlightInfo> highlightInfos = testFixture.doHighlighting(HighlightSeverity.ERROR);
    assertThat(highlightInfos).hasSize(1);
    assertThat(highlightInfos.get(0).type).isEqualTo(HighlightInfoType.WRONG_REF);
    assertThat(highlightInfos.get(0).getText()).isEqualTo("nonexistentString");
  }

  @Test
  public void testTopLevelClassCompletion_rClassInAutoComplete() {
    VirtualFile mainActivity =
        workspace.createFile(
            new WorkspacePath("java/com/foo/bar/MainActivity.java"),
            "package com.foo.bar;",
            "",
            "import android.app.Activity;",
            "import android.os.Bundle;",
            "",
            "public class MainActivity extends Activity {",
            "  @Override",
            "  protected void onCreate(Bundle savedInstanceState) {",
            "    super.onCreate(savedInstanceState);",
            "    getResources().getString(com.foo.bar." + CARET + ");",
            "  }",
            "}");

    workspace.createFile(
        new WorkspacePath("java/com/foo/bar/res/values/strings.xml"),
        "<resources>",
        "    <string name=\"appString\">Hello from app</string>",
        "</resources>");

    setTargetMap(android_library("//java/com/foo/bar:bar").src("MainActivity.java").res("res"));
    runFullBlazeSync();
    testFixture.configureFromExistingVirtualFile(mainActivity);
    testFixture.completeBasic();

    assertThat(testFixture.getLookupElementStrings()).containsExactly("MainActivity", "R");
  }

  @Test
  public void testResourceComesFromLightClass() {
    // Setup source file
    VirtualFile mainActivityFile =
        workspace.createFile(
            new WorkspacePath("java/com/foo/bar/MainActivity.java"),
            "package com.foo.bar;",
            "",
            "import android.app.Activity;",
            "import android.os.Bundle;",
            "",
            "public class MainActivity extends Activity {",
            "  @Override",
            "  protected void onCreate(Bundle savedInstanceState) {",
            "    super.onCreate(savedInstanceState);",
            "    getResources().getString(R.string." + CARET + "appString);",
            "  }",
            "}");

    // Source target creates a module for its resources and links to the aar exported by
    // //java/com/foo/baz:baz
    NbAndroidTarget sourceTarget =
        android_library("//java/com/foo/bar:bar")
            .src("MainActivity.java")
            .res("res")
            .manifest_value("package", "com.foo.bar")
            .dep("//java/com/foo/baz:baz");

    String stringXml =
        String.join(
            "\n",
            "<resources>",
            "    <string name=\"appString\">Hello from app</string>",
            "</resources>");
    workspace.createFile(new WorkspacePath("java/com/foo/baz/res/values/strings.xml"), stringXml);
    NbAndroidTarget depTarget =
        android_library("//java/com/foo/baz:baz")
            .res_folder("res", "lib_aar.aar")
            .manifest_value("package", "com.foo.baz");

    AarLibraryFileBuilder.aar(workspaceRoot, depTarget.getAarList().get(0).getRelativePath())
        .src("res/values/strings.xml", ImmutableList.of(stringXml))
        .build();

    setTargetMap(sourceTarget, depTarget);
    runFullBlazeSync();
    testFixture.configureFromExistingVirtualFile(mainActivityFile);

    testFixture.checkHighlighting();
    PsiElement elementUnderCaret = resolveReferenceUnderCaret();
    // Ensure that the resource comes from a LightElement instead of from a JAR
    assertThat(elementUnderCaret).isInstanceOf(LightElement.class);
  }

  /**
   * Returns the resolved PSIElement of the symbol around {@link #CARET}. Throws a {@link
   * NullPointerException} if no element is found under caret.
   */
  private PsiElement resolveReferenceUnderCaret() {
    return Objects.requireNonNull(TargetElementUtil.findReference(testFixture.getEditor()))
        .resolve();
  }
}
