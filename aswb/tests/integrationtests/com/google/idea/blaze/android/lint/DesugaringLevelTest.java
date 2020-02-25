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
package com.google.idea.blaze.android.lint;

import static com.google.common.truth.Truth.assertThat;

import com.android.builder.model.SourceProvider;
import com.android.ide.common.gradle.model.SourceProviderUtil;
import com.android.ide.common.util.PathStringUtil;
import com.android.projectmodel.AndroidPathType;
import com.android.projectmodel.SourceSet;
import com.android.tools.lint.detector.api.Desugaring;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.idea.blaze.android.sync.model.idea.BlazeAndroidModel;
import com.google.idea.blaze.android.sync.projectstructure.BlazeAndroidProjectStructureSyncerCompat;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.psi.PsiFile;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Set;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.AndroidFacetConfiguration;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Test to ensure Lint receives the correct Desugaring level. */
@RunWith(JUnit4.class)
public class DesugaringLevelTest extends LintIntegrationTestCase {

  private static final String BASE_PATH = "test";

  private static final String WORKSPACE_MODULE_NAME = ".workspace";
  private static final String WORKSPACE_MODULE_PATH =
      Paths.get(BASE_PATH, WORKSPACE_MODULE_NAME).toString();

  private Module workspaceModule;

  @Before
  public void lintSetup() {
    Module[] modules = ModuleManager.getInstance(getProject()).getModules();
    assertThat(modules).asList().hasSize(1);
    Module workspaceModule = modules[0];
    assertThat(workspaceModule.getName()).matches(WORKSPACE_MODULE_NAME);
    this.workspaceModule = workspaceModule;

    PsiFile dummyFile =
        testFixture.addFileToProject(
            Paths.get(BASE_PATH, "DummyClass.java").toString(),
            "package test.test;\n"
                + "public class DummyClass {\n"
                + "  public void noOpMethod() {}\n"
                + "}\n");
    PsiFile androidManifest =
        testFixture.addFileToProject(
            Paths.get(BASE_PATH, "AndroidManifest.xml").toString(),
            "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                + "    package=\"test.test\">\n"
                + "</manifest>\n");

    addContentEntryToModule(
        workspaceModule, dummyFile.getVirtualFile(), androidManifest.getVirtualFile());

    AndroidFacet facet =
        new AndroidFacet(
            workspaceModule, "workspace_android_facet", new AndroidFacetConfiguration());
    attachFacetToModule(workspaceModule, facet);

    Path androidManifestPath = Paths.get(androidManifest.getVirtualFile().getPath());
    SourceSet sourceSet =
        new SourceSet(
            ImmutableMap.of(
                AndroidPathType.MANIFEST,
                Collections.singletonList(PathStringUtil.toPathString(androidManifestPath))));
    SourceProvider sourceProvider =
        SourceProviderUtil.toSourceProvider(sourceSet, WORKSPACE_MODULE_NAME);

    BlazeAndroidModel blazeAndroidModel =
        new BlazeAndroidModel(
            getProject(), new File(BASE_PATH), sourceProvider, "test.test", 0, true);
    BlazeAndroidProjectStructureSyncerCompat.updateAndroidFacetWithSourceAndModel(
        facet, sourceProvider, blazeAndroidModel);

    testFixture.openFileInEditor(dummyFile.getVirtualFile());
  }

  @Ignore("b/150218451")
  @Test
  public void testLintDesugaringLevel() {
    PsiFile dummyFile = testFixture.getFile();
    // #as36: remove usage of compat class when 3.6 is no longer supported
    Set<Desugaring> desugaring =
        DesugaringLevelTestCompat.getDesugaringLevel(dummyFile, workspaceModule);
    assertThat(desugaring).containsExactlyElementsIn(Desugaring.FULL);
  }

  @Override
  protected boolean isLightTestCase() {
    return false;
  }

  @Override
  protected ImmutableList<String> getModuleNames() {
    return ImmutableList.of(WORKSPACE_MODULE_PATH);
  }
}
