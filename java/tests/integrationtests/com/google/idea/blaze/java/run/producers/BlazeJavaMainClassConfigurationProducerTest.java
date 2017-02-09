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
package com.google.idea.blaze.java.run.producers;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.BlazeIntegrationTestCase;
import com.google.idea.blaze.base.EditorTestHelper;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.ideinfo.JavaIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetMap;
import com.google.idea.blaze.base.ideinfo.TargetMapBuilder;
import com.google.idea.blaze.base.model.MockBlazeProjectDataBuilder;
import com.google.idea.blaze.base.model.MockBlazeProjectDataManager;
import com.google.idea.blaze.base.model.primitives.ExecutionRootPath;
import com.google.idea.blaze.base.model.primitives.TargetExpression;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.run.BlazeRunConfiguration;
import com.google.idea.blaze.base.sync.SyncCache;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.base.sync.workspace.BlazeRoots;
import com.intellij.execution.Location;
import com.intellij.execution.PsiLocation;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.MapDataContext;
import java.io.File;
import org.jetbrains.annotations.Nullable;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Integration tests for {@link BlazeJavaMainClassConfigurationProducer}. */
@RunWith(JUnit4.class)
public class BlazeJavaMainClassConfigurationProducerTest extends BlazeIntegrationTestCase {

  private EditorTestHelper editorTest;

  @Before
  public final void doSetup() {
    BlazeProjectDataManager mockProjectDataManager =
        new MockBlazeProjectDataManager(getMockBlazeProjectDataBuilder().build());
    registerProjectService(BlazeProjectDataManager.class, mockProjectDataManager);
    editorTest = new EditorTestHelper(getProject(), testFixture);
  }

  @After
  public final void doTearDown() {
    SyncCache.getInstance(getProject()).clear();
  }

  @Test
  public void testUniqueJavaBinaryChosen() {
    setTargets(
        TargetMapBuilder.builder()
            .addTarget(
                TargetIdeInfo.builder()
                    .setKind("java_binary")
                    .setLabel("//com/google/binary:UnrelatedName")
                    .addSource(sourceRoot("com/google/binary/MainClass.java"))
                    .build())
            .build());

    PsiFile javaClass =
        workspace.createPsiFile(
            WorkspacePath.createIfValid("com/google/binary/MainClass.java"),
            "package com.google.binary;",
            "import java.lang.String;",
            "public class MainClass {",
            "  public static void main(String[] args) {}",
            "}");

    RunConfiguration config = createConfigurationFromLocation(javaClass);

    assertThat(config).isInstanceOf(BlazeRunConfiguration.class);
    BlazeRunConfiguration blazeConfig = (BlazeRunConfiguration) config;
    assertThat(blazeConfig.getTarget())
        .isEqualTo(TargetExpression.fromString("//com/google/binary:UnrelatedName"));
  }

  @Test
  public void testNoJavaBinaryChosenIfNotInRDeps() {
    setTargets(
        TargetMapBuilder.builder()
            .addTarget(
                TargetIdeInfo.builder()
                    .setKind("java_binary")
                    .setLabel("//com/google/binary:MainClass")
                    .addSource(sourceRoot("com/google/binary/OtherClass.java"))
                    .build())
            .build());

    PsiFile javaClass =
        workspace.createPsiFile(
            WorkspacePath.createIfValid("com/google/binary/MainClass.java"),
            "package com.google.binary;",
            "import java.lang.String;",
            "public class MainClass {",
            "  public static void main(String[] args) {}",
            "}");

    assertThat(createConfigurationFromLocation(javaClass))
        .isNotInstanceOf(BlazeRunConfiguration.class);
  }

  @Test
  public void testNoResultForClassWithoutMainMethod() {
    setTargets(
        TargetMapBuilder.builder()
            .addTarget(
                TargetIdeInfo.builder()
                    .setKind("java_binary")
                    .setLabel("//com/google/binary:MainClass")
                    .addSource(sourceRoot("com/google/binary/MainClass.java"))
                    .setJavaInfo(JavaIdeInfo.builder().setMainClass("com.google.binary.MainClass"))
                    .build())
            .build());

    PsiFile javaClass =
        workspace.createPsiFile(
            WorkspacePath.createIfValid("com/google/binary/MainClass.java"),
            "package com.google.binary;",
            "public class MainClass {}");

    assertThat(createConfigurationFromLocation(javaClass)).isNull();
  }

  @Test
  public void testJavaBinaryWithMatchingNameChosen() {
    setTargets(
        TargetMapBuilder.builder()
            .addTarget(
                TargetIdeInfo.builder()
                    .setKind("java_binary")
                    .setLabel("//com/google/binary:UnrelatedName")
                    .addSource(sourceRoot("com/google/binary/MainClass.java"))
                    .build())
            .addTarget(
                TargetIdeInfo.builder()
                    .setKind("java_binary")
                    .setLabel("//com/google/binary:MainClass")
                    .addSource(sourceRoot("com/google/binary/MainClass.java"))
                    .build())
            .build());

    PsiFile javaClass =
        workspace.createPsiFile(
            WorkspacePath.createIfValid("com/google/binary/MainClass.java"),
            "package com.google.binary;",
            "import java.lang.String;",
            "public class MainClass {",
            "  public static void main(String[] args) {}",
            "}");

    RunConfiguration config = createConfigurationFromLocation(javaClass);
    assertThat(config).isInstanceOf(BlazeRunConfiguration.class);
    BlazeRunConfiguration blazeConfig = (BlazeRunConfiguration) config;
    assertThat(blazeConfig.getTarget())
        .isEqualTo(TargetExpression.fromString("//com/google/binary:MainClass"));
  }

  @Test
  public void testJavaBinaryWithMatchingMainClassChosen() {
    setTargets(
        TargetMapBuilder.builder()
            .addTarget(
                TargetIdeInfo.builder()
                    .setKind("java_binary")
                    .setLabel("//com/google/binary:UnrelatedName")
                    .addSource(sourceRoot("com/google/binary/MainClass.java"))
                    .build())
            .addTarget(
                TargetIdeInfo.builder()
                    .setKind("java_binary")
                    .setLabel("//com/google/binary:OtherName")
                    .setJavaInfo(JavaIdeInfo.builder().setMainClass("com.google.binary.MainClass"))
                    .addSource(sourceRoot("com/google/binary/MainClass.java"))
                    .build())
            .build());

    PsiFile javaClass =
        workspace.createPsiFile(
            WorkspacePath.createIfValid("com/google/binary/MainClass.java"),
            "package com.google.binary;",
            "import java.lang.String;",
            "public class MainClass {",
            "  public static void main(String[] args) {}",
            "}");

    RunConfiguration config = createConfigurationFromLocation(javaClass);

    assertThat(config).isInstanceOf(BlazeRunConfiguration.class);
    BlazeRunConfiguration blazeConfig = (BlazeRunConfiguration) config;
    assertThat(blazeConfig.getTarget())
        .isEqualTo(TargetExpression.fromString("//com/google/binary:OtherName"));
  }

  @Nullable
  private RunConfiguration createConfigurationFromLocation(PsiFile psiFile) {
    // a nauseating hack to force IntelliJ to recognize 'main' methods...
    workspace.createPsiFile(
        WorkspacePath.createIfValid("java/lang/String.java"),
        "package java.lang;",
        "public class String {}");
    editorTest.openFileInEditor(psiFile);

    final MapDataContext dataContext = new MapDataContext();

    dataContext.put(CommonDataKeys.PROJECT, getProject());
    dataContext.put(LangDataKeys.MODULE, ModuleUtil.findModuleForPsiElement(psiFile));
    dataContext.put(Location.DATA_KEY, PsiLocation.fromPsiElement(psiFile));
    RunnerAndConfigurationSettings settings =
        ConfigurationContext.getFromContext(dataContext).getConfiguration();
    return settings != null ? settings.getConfiguration() : null;
  }

  private MockBlazeProjectDataBuilder getMockBlazeProjectDataBuilder() {
    String executionRootPath = "usr/local/_blaze_";
    VirtualFile vf = fileSystem.createDirectory(executionRootPath);
    BlazeRoots fakeRoots =
        new BlazeRoots(
            new File(vf.getPath()),
            ImmutableList.of(workspaceRoot.directory()),
            new ExecutionRootPath("out/crosstool/bin"),
            new ExecutionRootPath("out/crosstool/gen"),
            null);
    return MockBlazeProjectDataBuilder.builder(workspaceRoot).setBlazeRoots(fakeRoots);
  }

  private void setTargets(TargetMap targets) {
    BlazeProjectDataManager mockProjectDataManager =
        new MockBlazeProjectDataManager(
            getMockBlazeProjectDataBuilder().setTargetMap(targets).build());
    registerProjectService(BlazeProjectDataManager.class, mockProjectDataManager);
    SyncCache.getInstance(getProject()).clear();
  }

  private static ArtifactLocation sourceRoot(String relativePath) {
    return ArtifactLocation.builder().setRelativePath(relativePath).setIsSource(true).build();
  }
}
