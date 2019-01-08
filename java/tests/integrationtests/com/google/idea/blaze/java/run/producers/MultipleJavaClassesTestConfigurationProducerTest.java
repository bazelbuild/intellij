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

import com.google.idea.blaze.base.command.BlazeCommandName;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetMapBuilder;
import com.google.idea.blaze.base.model.MockBlazeProjectDataBuilder;
import com.google.idea.blaze.base.model.MockBlazeProjectDataManager;
import com.google.idea.blaze.base.model.primitives.TargetExpression;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration;
import com.google.idea.blaze.base.run.producer.BlazeRunConfigurationProducerTestCase;
import com.google.idea.blaze.base.run.producers.TestContextRunConfigurationProducer;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.actions.ConfigurationFromContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.SourceFolder;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Integration tests for {@link MultipleJavaClassesTestContextProvider}. */
@RunWith(JUnit4.class)
public class MultipleJavaClassesTestConfigurationProducerTest
    extends BlazeRunConfigurationProducerTestCase {

  private SourceFolder javaSourceRoot;

  @Before
  public final void addSourceFolder() {
    // create a source root so that the package prefixes are correct.
    VirtualFile pkgRoot = workspace.createDirectory(new WorkspacePath("java"));
    ApplicationManager.getApplication()
        .runWriteAction(
            () -> {
              final ModifiableRootModel model =
                  ModuleRootManager.getInstance(testFixture.getModule()).getModifiableModel();
              ContentEntry contentEntry = model.getContentEntries()[0];
              javaSourceRoot = contentEntry.addSourceFolder(pkgRoot, false, "");
              model.commit();
            });

    BlazeProjectDataManager mockProjectDataManager =
        new MockBlazeProjectDataManager(MockBlazeProjectDataBuilder.builder(workspaceRoot).build());
    registerProjectService(BlazeProjectDataManager.class, mockProjectDataManager);
  }

  @After
  public final void removeSourceFolder() {
    ApplicationManager.getApplication()
        .runWriteAction(
            () -> {
              final ModifiableRootModel model =
                  ModuleRootManager.getInstance(testFixture.getModule()).getModifiableModel();
              ContentEntry contentEntry = model.getContentEntries()[0];
              contentEntry.removeSourceFolder(javaSourceRoot);
              model.commit();
            });
  }

  @Test
  public void testProducedFromDirectory() {
    MockBlazeProjectDataBuilder builder = MockBlazeProjectDataBuilder.builder(workspaceRoot);
    builder.setTargetMap(
        TargetMapBuilder.builder()
            .addTarget(
                TargetIdeInfo.builder()
                    .setKind("java_test")
                    .setLabel("//java/com/google/test:TestClass")
                    .addSource(sourceRoot("java/com/google/test/TestClass.java"))
                    .build())
            .build());
    registerProjectService(
        BlazeProjectDataManager.class, new MockBlazeProjectDataManager(builder.build()));

    PsiDirectory directory =
        workspace.createPsiDirectory(new WorkspacePath("java/com/google/test"));
    createAndIndexFile(
        new WorkspacePath("java/com/google/test/TestClass.java"),
        "package com.google.test;",
        "@org.junit.runner.RunWith(org.junit.runners.JUnit4.class)",
        "public class TestClass {",
        "  @org.junit.Test",
        "  public void testMethod() {}",
        "}");

    ConfigurationContext context = createContextFromPsi(directory);
    List<ConfigurationFromContext> configurations = context.getConfigurationsFromContext();
    assertThat(configurations).hasSize(1);

    ConfigurationFromContext fromContext = configurations.get(0);
    assertThat(fromContext.isProducedBy(TestContextRunConfigurationProducer.class)).isTrue();
    assertThat(fromContext.getConfiguration()).isInstanceOf(BlazeCommandRunConfiguration.class);

    BlazeCommandRunConfiguration config =
        (BlazeCommandRunConfiguration) fromContext.getConfiguration();
    assertThat(config.getTarget())
        .isEqualTo(TargetExpression.fromStringSafe("//java/com/google/test:TestClass"));
    assertThat(getTestFilterContents(config)).isEqualTo("--test_filter=com.google.test");
    assertThat(config.getName()).isEqualTo("Blaze test all in directory 'test'");
    assertThat(getCommandType(config)).isEqualTo(BlazeCommandName.TEST);
  }

  @Test
  public void testProducedFromDirectoryWithNestedTests() {
    MockBlazeProjectDataBuilder builder = MockBlazeProjectDataBuilder.builder(workspaceRoot);
    builder.setTargetMap(
        TargetMapBuilder.builder()
            .addTarget(
                TargetIdeInfo.builder()
                    .setKind("java_test")
                    .setLabel("//java/com/google/test:TestClass")
                    .addSource(sourceRoot("java/com/google/test/TestClass.java"))
                    .build())
            .build());
    registerProjectService(
        BlazeProjectDataManager.class, new MockBlazeProjectDataManager(builder.build()));

    PsiDirectory directory = workspace.createPsiDirectory(new WorkspacePath("java/com/google"));
    createAndIndexFile(
        new WorkspacePath("java/com/google/test/TestClass.java"),
        "package com.google.test;",
        "@org.junit.runner.RunWith(org.junit.runners.JUnit4.class)",
        "public class TestClass {",
        "  @org.junit.Test",
        "  public void testMethod() {}",
        "}");

    ConfigurationContext context = createContextFromPsi(directory);
    List<ConfigurationFromContext> configurations = context.getConfigurationsFromContext();
    assertThat(configurations).hasSize(1);

    ConfigurationFromContext fromContext = configurations.get(0);
    assertThat(fromContext.isProducedBy(TestContextRunConfigurationProducer.class)).isTrue();
    assertThat(fromContext.getConfiguration()).isInstanceOf(BlazeCommandRunConfiguration.class);

    BlazeCommandRunConfiguration config =
        (BlazeCommandRunConfiguration) fromContext.getConfiguration();
    assertThat(config.getTarget())
        .isEqualTo(TargetExpression.fromStringSafe("//java/com/google/test:TestClass"));
    assertThat(getTestFilterContents(config)).isEqualTo("--test_filter=com.google");
    assertThat(config.getName()).isEqualTo("Blaze test all in directory 'google'");
    assertThat(getCommandType(config)).isEqualTo(BlazeCommandName.TEST);
  }

  @Test
  public void testNoFilterIfDirectoryAtPackageRoot() {
    MockBlazeProjectDataBuilder builder = MockBlazeProjectDataBuilder.builder(workspaceRoot);
    builder.setTargetMap(
        TargetMapBuilder.builder()
            .addTarget(
                TargetIdeInfo.builder()
                    .setKind("java_test")
                    .setLabel("//java/com/google/test:TestClass")
                    .addSource(sourceRoot("java/com/google/test/TestClass.java"))
                    .build())
            .build());
    registerProjectService(
        BlazeProjectDataManager.class, new MockBlazeProjectDataManager(builder.build()));

    PsiDirectory directory = workspace.createPsiDirectory(new WorkspacePath("java"));
    createAndIndexFile(
        new WorkspacePath("java/com/google/test/TestClass.java"),
        "package com.google.test;",
        "@org.junit.runner.RunWith(org.junit.runners.JUnit4.class)",
        "public class TestClass {",
        "  @org.junit.Test",
        "  public void testMethod() {}",
        "}");

    ConfigurationContext context = createContextFromPsi(directory);
    List<ConfigurationFromContext> configurations = context.getConfigurationsFromContext();
    assertThat(configurations).hasSize(1);

    ConfigurationFromContext fromContext = configurations.get(0);
    assertThat(fromContext.isProducedBy(TestContextRunConfigurationProducer.class)).isTrue();
    assertThat(fromContext.getConfiguration()).isInstanceOf(BlazeCommandRunConfiguration.class);

    BlazeCommandRunConfiguration config =
        (BlazeCommandRunConfiguration) fromContext.getConfiguration();
    assertThat(getTestFilterContents(config)).isNull();
    assertThat(config.getName()).isEqualTo("Blaze test test:TestClass");
    assertThat(getCommandType(config)).isEqualTo(BlazeCommandName.TEST);
  }

  @Test
  public void testNotProducedFromDirectoryWithoutTests() {
    MockBlazeProjectDataBuilder builder = MockBlazeProjectDataBuilder.builder(workspaceRoot);
    builder.setTargetMap(
        TargetMapBuilder.builder()
            .addTarget(
                TargetIdeInfo.builder()
                    .setKind("java_test")
                    .setLabel("//java/com/google/test:TestClass")
                    .addSource(sourceRoot("java/com/google/test/TestClass.java"))
                    .build())
            .build());
    registerProjectService(
        BlazeProjectDataManager.class, new MockBlazeProjectDataManager(builder.build()));

    PsiDirectory directory =
        workspace.createPsiDirectory(new WorkspacePath("java/com/google/test"));

    ConfigurationContext context = createContextFromPsi(directory);
    assertThat(new TestContextRunConfigurationProducer().createConfigurationFromContext(context))
        .isNull();
  }

  @Test
  public void testProducedFromTestFiles() {
    MockBlazeProjectDataBuilder builder = MockBlazeProjectDataBuilder.builder(workspaceRoot);
    builder.setTargetMap(
        TargetMapBuilder.builder()
            .addTarget(
                TargetIdeInfo.builder()
                    .setKind("java_test")
                    .setLabel("//java/com/google/test:allTests")
                    .addSource(sourceRoot("java/com/google/test/TestClass1.java"))
                    .addSource(sourceRoot("java/com/google/test/TestClass2.java"))
                    .build())
            .build());
    registerProjectService(
        BlazeProjectDataManager.class, new MockBlazeProjectDataManager(builder.build()));

    workspace.createPsiDirectory(new WorkspacePath("java/com/google/test"));
    PsiFile testClass1 =
        createAndIndexFile(
            new WorkspacePath("java/com/google/test/TestClass1.java"),
            "package com.google.test;",
            "@org.junit.runner.RunWith(org.junit.runners.JUnit4.class)",
            "public class TestClass1 {",
            "  @org.junit.Test",
            "  public void testMethod() {}",
            "}");
    PsiFile testClass2 =
        createAndIndexFile(
            new WorkspacePath("java/com/google/test/TestClass2.java"),
            "package com.google.test;",
            "@org.junit.runner.RunWith(org.junit.runners.JUnit4.class)",
            "public class TestClass2 {",
            "  @org.junit.Test",
            "  public void testMethod() {}",
            "}");

    ConfigurationContext context =
        createContextFromMultipleElements(new PsiElement[] {testClass1, testClass2});
    ConfigurationFromContext fromContext =
        new TestContextRunConfigurationProducer().createConfigurationFromContext(context);
    assertThat(fromContext).isNotNull();
    assertThat(fromContext.getConfiguration()).isInstanceOf(BlazeCommandRunConfiguration.class);

    BlazeCommandRunConfiguration config =
        (BlazeCommandRunConfiguration) fromContext.getConfiguration();
    assertThat(config.getTarget())
        .isEqualTo(TargetExpression.fromStringSafe("//java/com/google/test:allTests"));
    assertThat(getTestFilterContents(config))
        .isEqualTo("--test_filter=com.google.test.TestClass1#|com.google.test.TestClass2#");
    assertThat(config.getName()).isEqualTo("Blaze test TestClass1 and 1 others");
    assertThat(getCommandType(config)).isEqualTo(BlazeCommandName.TEST);
  }

  @Test
  public void testNotProducedFromTestFilesInDifferentTestTargets() {
    MockBlazeProjectDataBuilder builder = MockBlazeProjectDataBuilder.builder(workspaceRoot);
    builder.setTargetMap(
        TargetMapBuilder.builder()
            .addTarget(
                TargetIdeInfo.builder()
                    .setKind("java_test")
                    .setLabel("//java/com/google/test:TestClass1")
                    .addSource(sourceRoot("java/com/google/test/TestClass1.java"))
                    .build())
            .addTarget(
                TargetIdeInfo.builder()
                    .setKind("java_test")
                    .setLabel("//java/com/google/test:TestClass2")
                    .addSource(sourceRoot("java/com/google/test/TestClass2.java"))
                    .build())
            .build());
    registerProjectService(
        BlazeProjectDataManager.class, new MockBlazeProjectDataManager(builder.build()));

    workspace.createPsiDirectory(new WorkspacePath("java/com/google/test"));
    PsiJavaFile testClass1 =
        (PsiJavaFile)
            createAndIndexFile(
                new WorkspacePath("java/com/google/test/TestClass1.java"),
                "package com.google.test;",
                "@org.junit.runner.RunWith(org.junit.runners.JUnit4.class)",
                "public class TestClass1 {",
                "  @org.junit.Test",
                "  public void testMethod() {}",
                "}");
    PsiJavaFile testClass2 =
        (PsiJavaFile)
            createAndIndexFile(
                new WorkspacePath("java/com/google/test/TestClass2.java"),
                "package com.google.test;",
                "@org.junit.runner.RunWith(org.junit.runners.JUnit4.class)",
                "public class TestClass2 {",
                "  @org.junit.Test",
                "  public void testMethod() {}",
                "}");

    ConfigurationContext context =
        createContextFromMultipleElements(
            new PsiElement[] {testClass1.getClasses()[0], testClass2.getClasses()[0]});
    assertThat(new TestContextRunConfigurationProducer().createConfigurationFromContext(context))
        .isNull();
  }

  @Test
  public void testNotProducedFromNonTestFiles() {
    MockBlazeProjectDataBuilder builder = MockBlazeProjectDataBuilder.builder(workspaceRoot);
    builder.setTargetMap(
        TargetMapBuilder.builder()
            .addTarget(
                TargetIdeInfo.builder()
                    .setKind("java_test")
                    .setLabel("//java/com/google/test:allTests")
                    .addSource(sourceRoot("java/com/google/test/NonTestClass1.java"))
                    .addSource(sourceRoot("java/com/google/test/NonTestClass2.java"))
                    .build())
            .build());
    registerProjectService(
        BlazeProjectDataManager.class, new MockBlazeProjectDataManager(builder.build()));

    workspace.createPsiDirectory(new WorkspacePath("java/com/google/test"));
    PsiFile testClass1 =
        createAndIndexFile(
            new WorkspacePath("java/com/google/test/NonTestClass1.java"),
            "package com.google.test;",
            "public class NonTestClass1 {");
    PsiFile testClass2 =
        createAndIndexFile(
            new WorkspacePath("java/com/google/test/NonTestClass2.java"),
            "package com.google.test;",
            "public class NonTestClass2 {}");

    ConfigurationContext context =
        createContextFromMultipleElements(new PsiElement[] {testClass1, testClass2});
    ConfigurationFromContext fromContext =
        new TestContextRunConfigurationProducer().createConfigurationFromContext(context);
    assertThat(fromContext).isNull();
  }
}
