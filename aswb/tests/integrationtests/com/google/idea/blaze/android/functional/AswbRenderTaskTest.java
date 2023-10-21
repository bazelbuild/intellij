/*
 * Copyright (C) 2018 The Android Open Source Project
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
import static com.google.idea.blaze.android.targetmapbuilder.NbAndroidTarget.android_library;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;

import com.android.ide.common.rendering.api.Result;
import com.android.ide.common.rendering.api.ViewInfo;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.rendering.AswbRenderTestUtils;
import com.android.tools.idea.rendering.RenderLogger;
import com.android.tools.idea.rendering.RenderResult;
import com.android.tools.idea.rendering.RenderService;
import com.android.tools.idea.rendering.RenderTask;
import com.google.common.util.concurrent.Futures;
import com.google.idea.blaze.android.AswbImageDiffUtil;
import com.google.idea.blaze.android.BlazeAndroidIntegrationTestCase;
import com.google.idea.blaze.android.MockSdkUtil;
import com.google.idea.blaze.android.fixtures.ManifestFixture;
import com.google.idea.blaze.android.libraries.LibraryFileBuilder;
import com.google.idea.blaze.android.targetmapbuilder.NbAndroidTarget;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.sync.projectstructure.ModuleFinder;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.containers.ImmutableList;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import org.intellij.lang.annotations.Language;
import org.jetbrains.android.facet.AndroidFacet;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests {@link RenderTask} for blaze projects. */
@RunWith(JUnit4.class)
public class AswbRenderTaskTest extends BlazeAndroidIntegrationTestCase {
  @Language("XML")
  private static final String LAYOUT_XML =
      "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
          + "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
          + "              android:layout_width=\"match_parent\"\n"
          + "              android:layout_height=\"match_parent\"\n"
          + "              android:orientation=\"vertical\">\n"
          + "  <Button\n"
          + "      android:layout_width=\"@dimen/ref_width\"\n"
          + "      android:layout_height=\"@dimen/ref_height\"\n"
          + "      android:background=\"#00F\"\n" // TODO(b/176114646) Remove solid background when
          // fixed.
          + "      android:text=\"ButtonText\"/>\n"
          + "  \n"
          + "  <ImageView\n"
          + "      android:layout_width=\"wrap_content\"\n"
          + "      android:layout_height=\"wrap_content\"\n"
          + "      android:src=\"@drawable/ic_banner\" />\n"
          + "</LinearLayout>";

  @Language("XML")
  private static final String DIMENS_XML =
      "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
          + "<resources>\n"
          + "  <dimen name=\"ref_height\">100px</dimen>\n"
          + "  <dimen name=\"ref_width\">200px</dimen>\n"
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
  public void testLayoutReferenceResourceFromSameModule() throws Exception {
    Sdk sdk = MockSdkUtil.registerSdk(workspace, "29");
    VirtualFile layoutXml =
        workspace.createFile(
            new WorkspacePath("java/com/foo/res/layout/activity_main.xml"), LAYOUT_XML);
    workspace.createFile(new WorkspacePath("java/com/foo/res/values/dimens.xml"), DIMENS_XML);
    VirtualFile icBannerPng =
        workspace.createFile(new WorkspacePath("java/com/foo/res/drawable/ic_banner.png"));
    WriteAction.run(
        () ->
            icBannerPng.setBinaryContent(
                Files.readAllBytes(getTestResource("testdata/ic_banner.png"))));

    setProjectView(
        "directories:",
        "  java/com/foo",
        "targets:",
        "  //java/com/foo:foo",
        "android_sdk_platform: android-stable");

    setTargetMap(
        android_library("//java/com/foo:foo")
            .res("res")
            .manifest(new ManifestFixture.Factory(getProject(), workspace).fromPackage("com.foo")));

    runFullBlazeSync();
    // RenderTask makes use of a real SDK instance.
    Module resourceModule = getResourceModuleWithSdk("java.com.foo.foo", sdk);
    RenderResult renderResult = withRenderTask(resourceModule, layoutXml);
    checkRenderResult(renderResult);
  }

  @Test
  public void testLayoutReferenceResourceFromModuleDependency() throws Exception {
    Sdk sdk = MockSdkUtil.registerSdk(workspace, "29");
    VirtualFile layoutXml =
        workspace.createFile(
            new WorkspacePath("java/com/foo/res/layout/activity_main.xml"), LAYOUT_XML);
    workspace.createFile(new WorkspacePath("java/com/bar/res/values/dimens.xml"), DIMENS_XML);
    VirtualFile icBannerPng =
        workspace.createFile(new WorkspacePath("java/com/bar/res/drawable/ic_banner.png"));
    WriteAction.run(
        () ->
            icBannerPng.setBinaryContent(
                Files.readAllBytes(getTestResource("testdata/ic_banner.png"))));

    // Module foo is dependent on module bar; bar is a module because it's included under
    // "directories" in project view.
    setProjectView(
        "directories:",
        "  java/com/foo",
        "  java/com/bar",
        "targets:",
        "  //java/com/foo:foo",
        "android_sdk_platform: android-stable");

    setTargetMap(
        android_library("//java/com/foo:foo")
            .dep("//java/com/bar:bar")
            .res("res")
            .manifest(new ManifestFixture.Factory(getProject(), workspace).fromPackage("com.foo")),
        android_library("//java/com/bar:bar")
            .res("res")
            .manifest(new ManifestFixture.Factory(getProject(), workspace).fromPackage("com.bar")));

    runFullBlazeSync();
    // RenderTask makes use of a real SDK instance.
    Module resourceModule = getResourceModuleWithSdk("java.com.foo.foo", sdk);
    RenderResult renderResult = withRenderTask(resourceModule, layoutXml);
    checkRenderResult(renderResult);
  }

  @Ignore("b/174590445")
  @Test
  public void testLayoutReferenceResourceFromLibraryDependency() throws Exception {
    Sdk sdk = MockSdkUtil.registerSdk(workspace, "29");
    VirtualFile layoutXml =
        workspace.createFile(
            new WorkspacePath("java/com/foo/res/layout/activity_main.xml"), LAYOUT_XML);
    workspace.createFile(new WorkspacePath("java/com/bar/res/values/dimens.xml"), DIMENS_XML);

    // Module foo is dependent on library bar; bar is a library because it's not included anywhere
    // in project view.
    setProjectView(
        "directories:",
        "  java/com/foo",
        "targets:",
        "  //java/com/foo:foo",
        "android_sdk_platform: android-stable");

    NbAndroidTarget bar =
        android_library("//java/com/bar:bar")
            .res_folder("//java/com/bar/res", "bar-java-com-bar-res.aar");
    LibraryFileBuilder.aar(workspaceRoot, bar.getAarList().get(0).getRelativePath())
        .addContent("res/values/dimens.xml", ImmutableList.singleton(DIMENS_XML))
        .addContent(
            "res/drawable/ic_banner.png",
            Files.readAllBytes(getTestResource("testdata/ic_banner.png")))
        .build();

    setTargetMap(
        android_library("//java/com/foo:foo")
            .dep("//java/com/bar:bar")
            .res("res")
            .manifest(new ManifestFixture.Factory(getProject(), workspace).fromPackage("com.foo")),
        bar);

    runFullBlazeSync();
    // RenderTask makes use of a real SDK instance.
    Module resourceModule = getResourceModuleWithSdk("java.com.foo.foo", sdk);
    RenderResult renderResult = withRenderTask(resourceModule, layoutXml);
    checkRenderResult(renderResult);
  }

  private Module getResourceModuleWithSdk(String moduleName, Sdk sdk) {
    Module resModule = ModuleFinder.getInstance(getProject()).findModuleByName(moduleName);
    ModuleRootModificationUtil.setModuleSdk(resModule, sdk);
    Disposer.register(
        getTestRootDisposable(),
        () -> WriteAction.run(() -> ProjectJdkTable.getInstance().removeJdk(sdk)));
    return resModule;
  }

  private static RenderResult withRenderTask(Module module, VirtualFile file) throws Exception {
    Configuration configuration = AswbRenderTestUtils.getConfiguration(module, file);
    RenderLogger logger = mock(RenderLogger.class);
    AndroidFacet facet = AndroidFacet.getInstance(module);
    PsiFile psiFile = PsiManager.getInstance(module.getProject()).findFile(file);
    assertNotNull(psiFile);
    RenderService renderService = RenderService.getInstance(module.getProject());
    final RenderTask task =
        renderService
            .taskBuilder(facet, configuration)
            .withLogger(logger)
            .withPsiFile(psiFile)
            .disableSecurityManager()
            .disableDecorations()
            .build()
            .get();

    assertNotNull(task);
    RenderResult result = Futures.getUnchecked(task.render());
    if (!task.isDisposed()) {
      task.dispose().get(5, TimeUnit.SECONDS);
    }
    return result;
  }

  /**
   * Asserts that the given result matches what is expected from {@link #LAYOUT_XML}. Here we expect
   * the inner linear layout's dimensions to match the dimensions declared in {@link #DIMENS_XML}
   * and the rendered image is identical to the golden image.
   */
  private static void checkRenderResult(RenderResult result) {
    assertThat(result.getRenderResult().getStatus()).isEqualTo(Result.Status.SUCCESS);
    ViewInfo view = result.getRootViews().get(0).getChildren().get(0);
    // The inner linear layout should be 200px wide and 100px high as defined in #DIMENS_XML.
    // We get the width and height by looking at the difference between layout bounds.
    assertThat(view.getRight() - view.getLeft()).isEqualTo(200);
    assertThat(view.getBottom() - view.getTop()).isEqualTo(100);

    try {
      BufferedImage golden = ImageIO.read(getTestResource("testdata/golden.png").toFile());
      BufferedImage rendered = result.getRenderedImage().getCopy();
      AswbImageDiffUtil.assertImageSimilar("renderedLayout", golden, rendered, 0);
    } catch (IOException e) {
      fail(e.getMessage());
    }
  }

  private static Path getTestResource(String relPath) {
    // When running from the IDE the working directory is the module containing the test sources.
    Path ideLocalPath = Paths.get(relPath);
    if (ideLocalPath.toFile().exists()) {
      return ideLocalPath;
    }

    // When running from bazel the working directory is the workspace root. Test data files
    // are located in the same directory as the test target itself.  Directory to the test
    // target is exposed as "TEST_BINARY" environment variable.
    Path bazelLocalPath = Paths.get(System.getenv("TEST_BINARY")).getParent().resolve(relPath);
    if (bazelLocalPath.toFile().exists()) {
      return bazelLocalPath;
    }

    fail("Test resource file " + relPath + " doesn't exist.");
    return null;
  }
}
