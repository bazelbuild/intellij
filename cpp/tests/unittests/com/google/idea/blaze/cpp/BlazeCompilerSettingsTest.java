/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.cpp;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.idea.blaze.base.BlazeTestCase;
import com.google.idea.blaze.base.bazel.BazelBuildSystemProvider;
import com.google.idea.blaze.base.bazel.BuildSystemProvider;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.MockBlazeProjectDataBuilder;
import com.google.idea.blaze.base.model.MockBlazeProjectDataManager;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.settings.BlazeImportSettings;
import com.google.idea.blaze.base.settings.BlazeImportSettings.ProjectType;
import com.google.idea.blaze.base.settings.BlazeImportSettingsManager;
import com.google.idea.blaze.base.settings.BuildSystemName;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.common.experiments.ExperimentService;
import com.google.idea.common.experiments.MockExperimentService;
import com.intellij.openapi.extensions.impl.ExtensionPointImpl;
import com.intellij.openapi.util.registry.Registry;
import com.jetbrains.cidr.lang.CLanguageKind;
import java.io.File;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link BlazeCompilerSettings}. */
@RunWith(JUnit4.class)
public class BlazeCompilerSettingsTest extends BlazeTestCase {

  private BlazeProjectData blazeProjectData;
  private WorkspaceRoot workspaceRoot;

  @Override
  protected void initTest(Container applicationServices, Container projectServices) {
    applicationServices.register(ExperimentService.class, new MockExperimentService());

    Registry.get("bazel.sync.resolve.virtual.includes").setValue(true);

    ExtensionPointImpl<BlazeCompilerFlagsProcessor.Provider> ep =
        registerExtensionPoint(
            BlazeCompilerFlagsProcessor.EP_NAME, BlazeCompilerFlagsProcessor.Provider.class);
    ep.registerExtension(new IncludeRootFlagsProcessor.Provider());
    ep.registerExtension(new SysrootFlagsProcessor.Provider());

    BlazeImportSettingsManager importSettingsManager = new BlazeImportSettingsManager(project);
    BlazeImportSettings importSettings =
        new BlazeImportSettings(
            "/root", "", "", "", BuildSystemName.Bazel, ProjectType.ASPECT_SYNC);
    importSettingsManager.setImportSettings(importSettings);
    projectServices.register(BlazeImportSettingsManager.class, importSettingsManager);

    workspaceRoot = WorkspaceRoot.fromImportSettings(importSettings);
    blazeProjectData = MockBlazeProjectDataBuilder.builder(workspaceRoot).build();
    projectServices.register(
        BlazeProjectDataManager.class, new MockBlazeProjectDataManager(blazeProjectData));
  }

  @Override
  protected BuildSystemProvider createBuildSystemProvider() {
    return new BazelBuildSystemProvider();
  }

  @Test
  public void testCompilerSwitchesSimple() {
    ImmutableList<String> cFlags = ImmutableList.of("-fast", "-slow");
    BlazeCompilerSettings settings =
        new BlazeCompilerSettings(
            getProject(),
            new File("bin/c"),
            new File("bin/c++"),
            cFlags,
            cFlags,
            "cc version (trunk r123456)",
            ImmutableMap.of());

    assertThat(settings.getCompilerSwitches(CLanguageKind.C, null))
        .containsExactly("-fast", "-slow")
        .inOrder();
  }

  @Test
  public void relativeSysroot_makesAbsolutePathInMainWorkspace() {
    ImmutableList<String> cFlags = ImmutableList.of("--sysroot=third_party/toolchain/");
    BlazeCompilerSettings settings =
        new BlazeCompilerSettings(
            getProject(),
            new File("bin/c"),
            new File("bin/c++"),
            cFlags,
            cFlags,
            "cc version (trunk r123456)",
            ImmutableMap.of());

    assertThat(settings.getCompilerSwitches(CLanguageKind.C, null))
        .containsExactly("--sysroot=" + workspaceRoot + "/third_party/toolchain");
  }

  @Test
  public void absoluteSysroot_doesNotChange() {
    ImmutableList<String> cFlags = ImmutableList.of("--sysroot=/usr");
    BlazeCompilerSettings settings =
        new BlazeCompilerSettings(
            getProject(),
            new File("bin/c"),
            new File("bin/c++"),
            cFlags,
            cFlags,
            "cc version (trunk r123456)",
            ImmutableMap.of());

    assertThat(settings.getCompilerSwitches(CLanguageKind.C, null))
        .containsExactly("--sysroot=/usr");
  }

  @Test
  public void relativeIsystem_makesAbsolutePathInWorkspaces() {
    ImmutableList<String> cFlags =
        ImmutableList.of("-isystem", "external/arm_gcc/include", "-DFOO=1", "-Ithird_party/stl");
    BlazeCompilerSettings settings =
        new BlazeCompilerSettings(
            getProject(),
            new File("bin/c"),
            new File("bin/c++"),
            cFlags,
            cFlags,
            "cc version (trunk r123456)",
            ImmutableMap.of());

    String outputBase = blazeProjectData.getBlazeInfo().getOutputBase().toString();
    assertThat(settings.getCompilerSwitches(CLanguageKind.C, null))
        .containsExactly(
            "-isystem",
            outputBase + "/external/arm_gcc/include",
            "-DFOO=1",
            "-I",
            workspaceRoot + "/third_party/stl");
  }

  @Test
  public void relativeIquote_makesAbsolutePathInExecRoot() {
    ImmutableList<String> cFlags =
        ImmutableList.of("-iquote", "bazel-out/android-arm64-v8a-opt/bin/external/boringssl");
    BlazeCompilerSettings settings =
        new BlazeCompilerSettings(
            getProject(),
            new File("bin/c"),
            new File("bin/c++"),
            cFlags,
            cFlags,
            "cc version (trunk r123456)",
            ImmutableMap.of());

    String execRoot = blazeProjectData.getBlazeInfo().getExecutionRoot().toString();
    assertThat(settings.getCompilerSwitches(CLanguageKind.C, null))
        .containsExactly(
            "-iquote",
            execRoot + "/bazel-out/android-arm64-v8a-opt/bin/external/boringssl");
  }

  @Test
  public void absoluteISystem_doesNotChange() {
    ImmutableList<String> cFlags = ImmutableList.of("-isystem", "/usr/include");
    BlazeCompilerSettings settings =
        new BlazeCompilerSettings(
            getProject(),
            new File("bin/c"),
            new File("bin/c++"),
            cFlags,
            cFlags,
            "cc version (trunk r123456)",
            ImmutableMap.of());

    assertThat(settings.getCompilerSwitches(CLanguageKind.C, null))
        .containsExactly("-isystem", "/usr/include");
  }

  @Test
  public void developerDirEnvVar_doesNotChange() {
    BlazeCompilerSettings settings =
        new BlazeCompilerSettings(
            getProject(),
            new File("bin/c"),
            new File("bin/c++"),
            ImmutableList.of(),
            ImmutableList.of(),
            "cc version (trunk r123456)",
            ImmutableMap.of("DEVELOPER_DIR", "/tmp/foobar"));

    assertThat(settings.getCompilerEnvironment("DEVELOPER_DIR"))
        .matches("/tmp/foobar");
  }
}
