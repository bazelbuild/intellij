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
package com.google.idea.blaze.golang.run.producers;

import static com.google.common.truth.Truth.assertThat;

import com.goide.project.GoPackageFactory;
import com.goide.psi.GoFile;
import com.goide.psi.GoFunctionDeclaration;
import com.goide.psi.impl.GoPackage;
import com.goide.psi.impl.imports.GoImportResolver;
import com.google.idea.blaze.base.command.BlazeCommandName;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetMapBuilder;
import com.google.idea.blaze.base.lang.buildfile.psi.util.PsiUtils;
import com.google.idea.blaze.base.model.MockBlazeProjectDataBuilder;
import com.google.idea.blaze.base.model.MockBlazeProjectDataManager;
import com.google.idea.blaze.base.model.primitives.TargetExpression;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration;
import com.google.idea.blaze.base.run.producers.BlazeRunConfigurationProducerTestCase;
import com.google.idea.blaze.base.run.producers.TestContextRunConfigurationProducer;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.golang.utils.MockGoImportResolver;
import com.google.idea.blaze.golang.utils.MockGoPackageFactory;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.actions.ConfigurationFromContext;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import java.util.List;
import java.util.Objects;

import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Integration tests for {@link GoTestContextProvider}. */
@RunWith(JUnit4.class)
public class BlazeGoTestConfigurationProducerTest extends BlazeRunConfigurationProducerTestCase {

  private final MockGoImportResolver importResolver = new MockGoImportResolver();
  private final MockGoPackageFactory packageFactory = new MockGoPackageFactory();
  @Before
  public final void init() {
    registerExtensionFirst(GoImportResolver.EP_NAME, importResolver);
    registerExtension(GoPackageFactory.EP_NAME, packageFactory);

    suppressNativeProducers();
    setupMockTestingPackage();
    setupMockTestifyPackage();
  }

  private void suppressNativeProducers() {
    // Project components triggered before we can set up BlazeImportSettings.
    NonBlazeProducerSuppressor.suppressProducers(getProject());
  }

  // These tests need to resolve references to the Go `testing` package.
  // We don't want to pull in a full Go SDK, so we mock just the bits we need here.
  private void setupMockTestingPackage() {
    GoFile testingSrc = (GoFile) workspace.createPsiFile(new WorkspacePath("testing/testing.go"),
        "package testing",
        "type T struct {}",
        "func (*T) Run(string, func(*T) {}"
    );
    // This has to be a custom implementation of GoPackage, because we want to bypass the usual
    // methods of figuring out the import path, as they are all broken if we don't have a full
    // sdk installed.
    GoPackage testingPkg = new GoPackage(getProject(), "testing", testingSrc.getContainingDirectory().getVirtualFile()) {
      public @NotNull String getImportPath(boolean withVendoring) {
        return "testing";
      }
    };
    importResolver.put("testing", testingPkg);
    packageFactory.put("testing", testingPkg);
  }

  private void setupMockTestifyPackage() {
    GoFile testifySrc = (GoFile)workspace.createPsiFile(
        new WorkspacePath("github.com/stretchr/testify/suite/suite.go"),
        "package suite",
        "type Suite struct {}",
        "func (suite *Suite) Run(name string, subtest func()) bool {}"
    );
    GoPackage testifyPkg = new GoPackage(getProject(), "suite",
        testifySrc.getContainingDirectory().getVirtualFile()) {
      public @NotNull String getImportPath(boolean withVendoring) {
        return "github.com/stretchr/testify/suite";
      }
    };
    importResolver.put("github.com/stretchr/testify/suite", testifyPkg);
    packageFactory.put("suite", testifyPkg);
  }

  private PsiFile setupWithSingleFile(String... contents) throws Throwable {
    PsiFile goFile =
        createAndIndexFile(
            new WorkspacePath("foo/bar/foo_test.go"),
        contents);

    MockBlazeProjectDataBuilder builder = MockBlazeProjectDataBuilder.builder(workspaceRoot);
    builder.setTargetMap(
        TargetMapBuilder.builder()
            .addTarget(
                TargetIdeInfo.builder()
                    .setKind("go_test")
                    .setLabel("//foo/bar:foo_test")
                    .addSource(sourceRoot("foo/bar/foo_test.go"))
                    .build())
            .build());
    registerProjectService(
        BlazeProjectDataManager.class, new MockBlazeProjectDataManager(builder.build()));

    testFixture.configureFromExistingVirtualFile(goFile.getVirtualFile());

    return goFile;
  }

  @Test
  public void testProducedFromGoFile() throws Throwable {
    PsiFile goFile = setupWithSingleFile(
        "package foo",
        "import \"testing\"",
        "func TestFoo(t *testing.T) {}"
    );
    ConfigurationContext context = createContextFromPsi(goFile);
    List<ConfigurationFromContext> configurations = context.getConfigurationsFromContext();
    assertThat(configurations).isNotNull();
    assertThat(configurations).hasSize(1);

    ConfigurationFromContext fromContext = configurations.get(0);
    assertThat(fromContext.isProducedBy(TestContextRunConfigurationProducer.class)).isTrue();
    assertThat(fromContext.getConfiguration()).isInstanceOf(BlazeCommandRunConfiguration.class);

    BlazeCommandRunConfiguration config =
        (BlazeCommandRunConfiguration) fromContext.getConfiguration();
    assertThat(config.getTargets())
        .containsExactly(TargetExpression.fromStringSafe("//foo/bar:foo_test"));
    assertThat(getTestFilterContents(config)).isNull();
    assertThat(getCommandType(config)).isEqualTo(BlazeCommandName.TEST);
  }

  @Test
  public void testTestifyFile() throws Throwable {
    PsiFile goFile = setupWithSingleFile(
        """
            package foo
            import "github.com/stretchr/testify/suite"
            
            type ExampleTestSuite struct {
                suite.Suite
                VariableThatShouldStartAtFive int
            }
            
            func (suite *ExampleTestSuite) Test<caret>Example() {
            }
            
            func TestExampleTestSuite(t *testing.T) {
                suite.Run(t, new(ExampleTestSuite))
            }
            """
    );

    PsiElement element = getElementAtCaret(0, goFile);
    String expectedTestFilter = "^TestExample$";
    ConfigurationContext context = createContextFromPsi(element);
    List<ConfigurationFromContext> configurations = context.getConfigurationsFromContext();
    assertThat(configurations).isNotNull();
    assertThat(configurations).hasSize(1);

    ConfigurationFromContext fromContext = configurations.get(0);
    assertThat(fromContext.isProducedBy(TestContextRunConfigurationProducer.class)).isTrue();
    assertThat(fromContext.getConfiguration()).isInstanceOf(BlazeCommandRunConfiguration.class);

    BlazeCommandRunConfiguration config =
        (BlazeCommandRunConfiguration)fromContext.getConfiguration();
    assertThat(config.getTargets())
        .containsExactly(TargetExpression.fromStringSafe("//foo/bar:foo_test"));
    assertThat(Objects.requireNonNull(getTestArgsContents(config)).get(0)).isEqualTo(
        "-testify.m=" + expectedTestFilter);
    assertThat(getCommandType(config)).isEqualTo(BlazeCommandName.TEST);
  }

  @Test
  public void testProducedFromTestCase() throws Throwable {
    PsiFile goFile = setupWithSingleFile(
            "package foo",
            "import \"testing\"",
            "func T<caret>estFoo(t *testing.T) {}");

    assertElementGeneratesTestFilter(getElementAtCaret(0, goFile), "^TestFoo$");
  }

  @Test
  public void testNestedTests() throws Throwable {
    PsiFile goFile = setupWithSingleFile(
        "package lib",
        "import \"testing\"",
        "func TestFoo(t *testing.T) {",
        "   t.R<caret>un(\"with_nested\", func(t *testing.T) {",
        "     t.R<caret>un(\"subtest\", func(t *testing.T) {})",
        "     t.R<caret>un(\"subtest (great)\", func(t *testing.T) {})",
        "   })",
        "}"
    );

    assertElementGeneratesTestFilter(getElementAtCaret(0, goFile), "^TestFoo/with_nested$");
    assertElementGeneratesTestFilter(getElementAtCaret(1, goFile), "^TestFoo/with_nested/subtest$");
    assertElementGeneratesTestFilter(getElementAtCaret(2, goFile), "^TestFoo/with_nested/subtest_\\(great\\)$");
  }
  @Test
  public void testCodeBetweenTests() throws Throwable {
    PsiFile goFile = setupWithSingleFile(
        "package foo",
        "import (",
        "   \"testing\"",
        "   \"fmt\"",
        ")",
        "func TestFoo(t *testing.T) {",
        "   fmt.Println(\"Just some other call\")",
        "   t.R<caret>un(\"subtest\", func(t *testing.T) {})",
        "}"
    );

    assertElementGeneratesTestFilter(getElementAtCaret(0, goFile), "^TestFoo/subtest$");
  }

  @Test
  public void testTestsInStruct() throws Throwable {
    PsiFile goFile = setupWithSingleFile(
        "package foo",
        "import (",
        "   \"testing\"",
        "   \"fmt\"",
        ")",
        "func TestFoo(t *testing.T) {",
        "  testCases := []struct { name string }{ ",
        "     {<caret> name: \"TestCase1\",},",
        "     {<caret> name: \"TestCase2\",},",
        "  }",
        "  ",
        "  for _, tc := range testCases {",
        "    t.Run(tc.name, func(t *testing.T) {",
        "      log.Println(\"running test: \" + tc.name)",
        "    })",
        "  }",
        "}"
    );

    assertElementGeneratesTestFilter(getElementAtCaret(0, goFile), "^TestFoo/TestCase1$");
    assertElementGeneratesTestFilter(getElementAtCaret(1, goFile), "^TestFoo/TestCase2$");
  }

  private PsiElement getElementAtCaret(int caretIndex, PsiFile goFile) {
    Editor editor = testFixture.getEditor();
    Caret caret = editor.getCaretModel().getAllCarets().get(caretIndex);
    return goFile.findElementAt(caret.getOffset());
  }

  private void assertElementGeneratesTestFilter(PsiElement element, String expectedTestFilter) {
    ConfigurationContext context = createContextFromPsi(element);
    List<ConfigurationFromContext> configurations = context.getConfigurationsFromContext();
    assertThat(configurations).isNotNull();
    assertThat(configurations).hasSize(1);

    ConfigurationFromContext fromContext = configurations.get(0);
    assertThat(fromContext.isProducedBy(TestContextRunConfigurationProducer.class)).isTrue();
    assertThat(fromContext.getConfiguration()).isInstanceOf(BlazeCommandRunConfiguration.class);

    BlazeCommandRunConfiguration config =
        (BlazeCommandRunConfiguration) fromContext.getConfiguration();
    assertThat(config.getTargets())
        .containsExactly(TargetExpression.fromStringSafe("//foo/bar:foo_test"));
    assertThat(getTestFilterContents(config)).isEqualTo("--test_filter=" + expectedTestFilter);
    assertThat(getCommandType(config)).isEqualTo(BlazeCommandName.TEST);
  }
}
