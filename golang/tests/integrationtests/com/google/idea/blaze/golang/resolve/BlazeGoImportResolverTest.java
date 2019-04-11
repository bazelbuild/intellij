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
package com.google.idea.blaze.golang.resolve;

import static com.google.common.truth.Truth.assertThat;

import com.goide.psi.GoFieldDefinition;
import com.goide.psi.GoFile;
import com.goide.psi.GoMethodDeclaration;
import com.goide.psi.GoTypeSpec;
import com.goide.psi.impl.GoPackage;
import com.google.idea.blaze.base.BlazeIntegrationTestCase;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.ideinfo.GoIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetMap;
import com.google.idea.blaze.base.ideinfo.TargetMapBuilder;
import com.google.idea.blaze.base.lang.buildfile.psi.FuncallExpression;
import com.google.idea.blaze.base.model.MockBlazeProjectDataBuilder;
import com.google.idea.blaze.base.model.MockBlazeProjectDataManager;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.golang.BlazeGoSupport;
import com.google.idea.common.experiments.ExperimentService;
import com.google.idea.common.experiments.MockExperimentService;
import com.intellij.codeInsight.navigation.actions.GotoDeclarationAction;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Editor;
import com.intellij.pom.PomTarget;
import com.intellij.pom.PomTargetPsiElement;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import javax.annotation.Nullable;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Integration tests for {@link BlazeGoImportResolver}. */
@RunWith(JUnit4.class)
public class BlazeGoImportResolverTest extends BlazeIntegrationTestCase {
  @Before
  public void init() {
    MockExperimentService experimentService = new MockExperimentService();
    experimentService.setExperiment(BlazeGoSupport.blazeGoSupportEnabled, true);
    registerApplicationComponent(ExperimentService.class, experimentService);
    TargetMap targetMap =
        TargetMapBuilder.builder()
            .addTarget(
                TargetIdeInfo.builder()
                    .setBuildFile(src("binary/BUILD"))
                    .setLabel("//binary:main")
                    .setKind("go_binary")
                    .addSource(src("binary/main.go"))
                    .addDependency("//library:foo")
                    .addDependency("//library/bar:go_default_library")
                    .setGoInfo(
                        GoIdeInfo.builder()
                            .addSource(src("binary/main.go"))
                            .setImportPath("github.com/user/binary/main")))
            .addTarget(
                TargetIdeInfo.builder()
                    .setBuildFile(src("library/BUILD"))
                    .setLabel("//library:foo")
                    .setKind("go_library")
                    .addSource(src("library/foo.go"))
                    .addSource(src("library/foo2.go"))
                    .addSource(src("library/foo/nested.go"))
                    .setGoInfo(
                        GoIdeInfo.builder()
                            .addSource(src("library/foo.go"))
                            .addSource(src("library/foo2.go"))
                            .addSource(src("library/foo/nested.go"))
                            .setImportPath("github.com/user/library/foo")))
            .addTarget(
                TargetIdeInfo.builder()
                    .setBuildFile(src("library/bar/BUILD"))
                    .setLabel("//library/bar:go_default_library")
                    .setKind("go_library")
                    .addSource(src("library/bar/bar.go"))
                    .setGoInfo(
                        GoIdeInfo.builder()
                            .addSource(src("library/bar/bar.go"))
                            .setImportPath("github.com/user/library/bar")))
            .addTarget(
                TargetIdeInfo.builder()
                    .setBuildFile(src("library/BUILD"))
                    .setLabel("//library:foo_private_test")
                    .setKind("go_test")
                    .addSource(src("library/foo_private_test.go"))
                    .addDependency("//library:foo")
                    .setGoInfo(
                        GoIdeInfo.builder()
                            .addSource(src("library/foo_private_test.go"))
                            .setImportPath("github.com/user/library/foo_private_test")
                            .setLibraryLabel("//library:foo")))
            .addTarget(
                TargetIdeInfo.builder()
                    .setBuildFile(src("library/BUILD"))
                    .setLabel("//library:foo_private2_test")
                    .setKind("go_test")
                    .addSource(src("library/foo_private2_test.go"))
                    .addDependency("//library:foo")
                    .setGoInfo(
                        GoIdeInfo.builder()
                            .addSource(src("library/foo_private2_test.go"))
                            .setImportPath("github.com/user/library/foo_private2_test")
                            .setLibraryLabel("//library:foo")))
            .addTarget(
                TargetIdeInfo.builder()
                    .setBuildFile(src("library/BUILD"))
                    .setLabel("//library:foo_public_test")
                    .setKind("go_test")
                    .addSource(src("library/foo_public_test.go"))
                    .addDependency("//library:foo")
                    .setGoInfo(
                        GoIdeInfo.builder()
                            .addSource(src("library/foo_public_test.go"))
                            .setImportPath("github.com/user/library/foo_public_test")))
            .build();
    registerProjectService(
        BlazeProjectDataManager.class,
        new MockBlazeProjectDataManager(
            MockBlazeProjectDataBuilder.builder(workspaceRoot).setTargetMap(targetMap).build()));
    workspace.createPsiFile(
        new WorkspacePath("binary/BUILD"),
        "go_binary(",
        "    name = \"main\",",
        "    srcs = [\"main.go\"],", // created during test
        "    deps = [",
        "        \"//library:foo\",",
        "        \"//library/bar:go_default_library\",",
        "    ],",
        ")");
    workspace.createPsiFile(
        new WorkspacePath("library/BUILD"),
        "go_library(",
        "    name = \"foo\",",
        "    srcs = [",
        "        \"foo.go\",",
        "        \"foo/nested.go\",",
        "        \"foo2.go\",", // created during test
        "    ],",
        ")",
        "go_test(",
        "    name = \"foo_private_test\",",
        "    srcs = [\"foo_private_test.go\"],", // created during test
        "    library = \":foo\",",
        ")",
        "go_test(",
        "    name = \"foo_private2_test\",",
        "    srcs = [\"foo_private2_test.go\"],", // created during test
        "    library = \":foo\",",
        ")",
        "go_test(",
        "    name = \"foo_public_test\",",
        "    srcs = [\"foo_public_test.go\"],", // created during test
        "    deps = [\":foo\"],",
        ")");
    workspace.createPsiFile(
        new WorkspacePath("library/bar/BUILD"),
        "go_library(",
        "    name = \"go_default_library\",",
        "    srcs = [\"bar.go\"],",
        ")");
    workspace.createPsiFile(
        new WorkspacePath("library/foo.go"),
        "package foo",
        "type Foo struct {",
        "\tPublic  int",
        "\tprivate int",
        "}",
        "func (f Foo) Run() {}",
        "func (f Foo) run() {}");
    workspace.createPsiFile(
        new WorkspacePath("library/foo/nested.go"),
        "package foo",
        "type FooNested struct{}",
        "func (fn FooNested) Run() {}");
    workspace.createPsiFile(
        new WorkspacePath("library/bar/bar.go"),
        "package bar",
        "type Foo struct{}",
        "func (b Foo) Run() {}");
  }

  @Test
  public void testResolveImportPathToRule() {
    configureGoFile(
        "binary/main.go",
        "package main",
        "",
        "import \"g<caret>ithub.com/u<caret>ser/l<caret>ibrary/f<caret>oo\"",
        "",
        "func main() {}");
    assertThatResolveCaret(0).isNull(); // github.com
    assertThatResolveCaret(1).isNull(); // user
    assertThatResolveCaret(2).typeIs(PsiDirectory.class).nameIs("library");
    assertThatResolveCaret(3).typeIs(FuncallExpression.class).nameIs("foo");
  }

  @Test
  public void testResolveImportPathToDirectory() {
    configureGoFile(
        "binary/main.go",
        "package main",
        "",
        "import \"g<caret>ithub.com/u<caret>ser/l<caret>ibrary/b<caret>ar\"",
        "",
        "func main() {}");
    assertThatResolveCaret(0).isNull(); // github.com
    assertThatResolveCaret(1).isNull(); // user
    assertThatResolveCaret(2).typeIs(PsiDirectory.class).nameIs("library");
    assertThatResolveCaret(3).typeIs(PsiDirectory.class).nameIs("bar");
  }

  @Test
  public void testResolvePublicLibrarySymbolsFromBinary() {
    configureGoFile(
        "binary/main.go",
        "package main",
        "import \"github.com/user/library/foo\"",
        "import \"github.com/user/library/bar\"",
        "",
        "func main() {",
        "\tfo<caret>o.Fo<caret>o{Publi<caret>c: 0}.Ru<caret>n()",
        "\tfo<caret>o.Fo<caret>oNested{}.Ru<caret>n()",
        "\tba<caret>r.Fo<caret>o{}.Ru<caret>n()",
        "}");

    assertThatResolveCaret(0).typeIs(FuncallExpression.class).nameIs("foo");
    assertThatResolveCaret(1).typeIs(GoTypeSpec.class).nameIs("Foo").inFile("foo.go");
    assertThatResolveCaret(2).typeIs(GoFieldDefinition.class).nameIs("Public").inFile("foo.go");
    assertThatResolveCaret(3).typeIs(GoMethodDeclaration.class).nameIs("Run").inFile("foo.go");

    assertThatResolveCaret(4).typeIs(FuncallExpression.class).nameIs("foo");
    assertThatResolveCaret(5).typeIs(GoTypeSpec.class).nameIs("FooNested").inFile("nested.go");
    assertThatResolveCaret(6).typeIs(GoMethodDeclaration.class).nameIs("Run").inFile("nested.go");

    assertThatResolveCaret(7).typeIs(FuncallExpression.class).nameIs("go_default_library");
    assertThatResolveCaret(8).typeIs(GoTypeSpec.class).nameIs("Foo").inFile("bar.go");
    assertThatResolveCaret(9).typeIs(GoMethodDeclaration.class).nameIs("Run").inFile("bar.go");
  }

  @Test
  public void testResolvePrivateLibrarySymbolsFromSameLibrary() {
    configureGoFile(
        "library/foo2.go",
        "package foo",
        "",
        "func test() {",
        "\tf := Fo<caret>o{privat<caret>e: 0}",
        "\tf.ru<caret>n()",
        "}");
    assertThatResolveCaret(0).typeIs(GoTypeSpec.class).nameIs("Foo").inFile("foo.go");
    assertThatResolveCaret(1).typeIs(GoFieldDefinition.class).nameIs("private").inFile("foo.go");
    assertThatResolveCaret(2).typeIs(GoMethodDeclaration.class).nameIs("run").inFile("foo.go");
  }

  @Test
  public void testResolveTestSymbolsFromLibrary() {
    workspace.createPsiFile(
        new WorkspacePath("library/foo_private_test.go"),
        "package foo",
        "import \"testing\"",
        "type TestStruct struct{}",
        "func (ts TestStruct) Run() {}",
        "",
        "type testStruct struct{}",
        "func (ts testStruct) run() {}");
    configureGoFile(
        "library/foo2.go",
        "package foo",
        "type LibraryStruct struct{}",
        "func (ls LibraryStruct) Run2() {}",
        "",
        "func testGood() {",
        "\tLibraryStruc<caret>t{}.Ru<caret>n2()",
        "}",
        "",
        "func testBad() {",
        "\tTestStruc<caret>t{}.Ru<caret>n()",
        "\ttestStruc<caret>t{}.ru<caret>n()",
        "}");
    assertThatResolveCaret(0).typeIs(GoTypeSpec.class).nameIs("LibraryStruct").inFile("foo2.go");
    assertThatResolveCaret(1).typeIs(GoMethodDeclaration.class).nameIs("Run2").inFile("foo2.go");
    assertThatResolveCaret(2).isNull(); // TestStruct{}
    assertThatResolveCaret(3).isNull(); // .Run()
    assertThatResolveCaret(4).isNull(); // testStruct{}
    assertThatResolveCaret(5).isNull(); // .run()
  }

  @Test
  public void testResolvePublicLibrarySymbolsFromPrivateTest() {
    configureGoFile(
        "library/foo_private_test.go",
        "package foo",
        "import \"testing\"",
        "",
        "func Test(t *testing.T) {",
        "\tf := Fo<caret>o{Publi<caret>c: 0}",
        "\tf.Ru<caret>n()",
        "}");
    assertThatResolveCaret(0).typeIs(GoTypeSpec.class).nameIs("Foo").inFile("foo.go");
    assertThatResolveCaret(1).typeIs(GoFieldDefinition.class).nameIs("Public").inFile("foo.go");
    assertThatResolveCaret(2).typeIs(GoMethodDeclaration.class).nameIs("Run").inFile("foo.go");
  }

  /**
   * Either this or {@link #testResolveTestSymbolsFromAnotherPrivateTest()}
   *
   * <p>https://youtrack.jetbrains.com/issue/GO-6616
   */
  @Test
  public void testResolvePrivateLibrarySymbolsFromPrivateTest() {
    configureGoFile(
        "library/foo_private_test.go",
        "package foo",
        "import \"testing\"",
        "",
        "func Test(t *testing.T) {",
        "\tf := Fo<caret>o{privat<caret>e: 0}",
        "\tf.ru<caret>n()",
        "}");
    assertThatResolveCaret(0).typeIs(GoTypeSpec.class).nameIs("Foo").inFile("foo.go");
    assertThatResolveCaret(1).typeIs(GoFieldDefinition.class).nameIs("private").inFile("foo.go");
    assertThatResolveCaret(2).typeIs(GoMethodDeclaration.class).nameIs("run").inFile("foo.go");
  }

  @Test
  public void testResolvePublicLibrarySymbolsFromPublicTest() {
    configureGoFile(
        "library/foo_public_test.go",
        "package foo_test",
        "import \"github.com/user/library/foo\"",
        "import \"testing\"",
        "",
        "func Test(t *testing.T) {",
        "\tf := fo<caret>o.Fo<caret>o{Publi<caret>c: 0}",
        "\tf.Ru<caret>n()",
        "}");
    assertThatResolveCaret(0).typeIs(FuncallExpression.class).nameIs("foo");
    assertThatResolveCaret(1).typeIs(GoTypeSpec.class).nameIs("Foo").inFile("foo.go");
    assertThatResolveCaret(2).typeIs(GoFieldDefinition.class).nameIs("Public").inFile("foo.go");
    assertThatResolveCaret(3).typeIs(GoMethodDeclaration.class).nameIs("Run").inFile("foo.go");
  }

  @Test
  public void testResolveTestSymbolsFromSamePrivateTest() {
    configureGoFile(
        "library/foo_private_test.go",
        "package foo",
        "import \"testing\"",
        "type TestStruct struct{}",
        "func (ts TestStruct) Run() {}",
        "",
        "type testStruct struct{}",
        "func (ts testStruct) run() {}",
        "",
        "func Test(t *testing.T) {",
        "\tTestStruc<caret>t{}.Ru<caret>n()",
        "\ttestStruc<caret>t{}.ru<caret>n()",
        "}");
    assertThatResolveCaret(0)
        .typeIs(GoTypeSpec.class)
        .nameIs("TestStruct")
        .inFile("foo_private_test.go");
    assertThatResolveCaret(1)
        .typeIs(GoMethodDeclaration.class)
        .nameIs("Run")
        .inFile("foo_private_test.go");
    assertThatResolveCaret(2)
        .typeIs(GoTypeSpec.class)
        .nameIs("testStruct")
        .inFile("foo_private_test.go");
    assertThatResolveCaret(3)
        .typeIs(GoMethodDeclaration.class)
        .nameIs("run")
        .inFile("foo_private_test.go");
  }

  /**
   * Either this or {@link #testResolvePrivateLibrarySymbolsFromPrivateTest()}
   *
   * <p>https://youtrack.jetbrains.com/issue/GO-6616
   */
  @Test
  @Ignore
  public void testResolveTestSymbolsFromAnotherPrivateTest() {
    workspace.createPsiFile(
        new WorkspacePath("library/foo_private_test.go"),
        "package foo",
        "import \"testing\"",
        "type TestStruct struct{}",
        "func (ts TestStruct) Run() {}",
        "",
        "type testStruct struct{}",
        "func (t testStruct) run() {}");
    configureGoFile(
        "library/foo_private2_test.go",
        "package foo",
        "import \"testing\"",
        "type Test2Struct struct{}",
        "func (t2s Test2Struct) Run2() {}",
        "",
        "func TestGood(t *testing.T) {",
        "\tTest2Struc<caret>t{}.Ru<caret>n2()",
        "}",
        "",
        "func TestBad(t *testing.T) {",
        "\tTestStruc<caret>t{}.Ru<caret>n()",
        "\ttestStruc<caret>t{}.ru<caret>n()",
        "}");
    assertThatResolveCaret(0)
        .typeIs(GoTypeSpec.class)
        .nameIs("Test2Struct")
        .inFile("foo_private2_test.go");
    assertThatResolveCaret(1)
        .typeIs(GoMethodDeclaration.class)
        .nameIs("Run2")
        .inFile("foo_private2_test.go");
    assertThatResolveCaret(2).isNull(); // TestStruct{}
    assertThatResolveCaret(3).isNull(); // .Run()
    assertThatResolveCaret(4).isNull(); // testStruct{}
    assertThatResolveCaret(5).isNull(); // .run()
  }

  @Test
  public void testResolveTestSymbolsFromSamePublicTest() {
    configureGoFile(
        "library/foo_public_test.go",
        "package foo_test",
        "import \"testing\"",
        "type TestStruct struct{}",
        "func (ts TestStruct) Run() {}",
        "",
        "type testStruct struct{}",
        "func (ts testStruct) run() {}",
        "",
        "func Test(t *testing.T) {",
        "\tTestStruc<caret>t{}.Ru<caret>n()",
        "\ttestStruc<caret>t{}.ru<caret>n()",
        "}");
    assertThatResolveCaret(0)
        .typeIs(GoTypeSpec.class)
        .nameIs("TestStruct")
        .inFile("foo_public_test.go");
    assertThatResolveCaret(1)
        .typeIs(GoMethodDeclaration.class)
        .nameIs("Run")
        .inFile("foo_public_test.go");
    assertThatResolveCaret(2)
        .typeIs(GoTypeSpec.class)
        .nameIs("testStruct")
        .inFile("foo_public_test.go");
    assertThatResolveCaret(3)
        .typeIs(GoMethodDeclaration.class)
        .nameIs("run")
        .inFile("foo_public_test.go");
  }

  private static ArtifactLocation src(String relativePath) {
    return ArtifactLocation.builder().setRelativePath(relativePath).setIsSource(true).build();
  }

  private void configureGoFile(String path, String... contentLines) {
    GoFile goFile = (GoFile) workspace.createPsiFile(new WorkspacePath(path), contentLines);
    testFixture.configureFromExistingVirtualFile(goFile.getVirtualFile());
  }

  private static class ResolvedSubject {
    @Nullable private final PsiElement resolved;

    private ResolvedSubject(@Nullable PsiElement resolved) {
      this.resolved = resolved;
    }

    void isNull() {
      assertThat(resolved).isNull();
    }

    ResolvedSubject nameIs(String name) {
      assertThat(resolved).isNotNull();
      assertThat(((PsiNamedElement) resolved).getName()).isEqualTo(name);
      return this;
    }

    ResolvedSubject typeIs(Class<? extends PsiElement> type) {
      assertThat(resolved).isInstanceOf(type);
      return this;
    }

    void inFile(String fileName) {
      assertThat(resolved).isNotNull();
      assertThat(resolved.getContainingFile().getName()).isEqualTo(fileName);
    }
  }

  private ResolvedSubject assertThatResolveCaret(int index) {
    Editor editor = testFixture.getEditor();
    Caret caret = editor.getCaretModel().getAllCarets().get(index);
    PsiElement resolved =
        GotoDeclarationAction.findTargetElement(getProject(), editor, caret.getOffset());
    if (resolved instanceof PomTargetPsiElement) {
      PomTarget target = ((PomTargetPsiElement) resolved).getTarget();
      if (target instanceof GoPackage) {
        resolved = ((GoPackage) target).getNavigableElement();
      }
    } else if (resolved != null) {
      resolved = resolved.getNavigationElement();
    }
    return new ResolvedSubject(resolved);
  }
}
