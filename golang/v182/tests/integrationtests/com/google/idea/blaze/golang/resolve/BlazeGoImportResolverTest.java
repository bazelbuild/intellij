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

import com.goide.psi.GoFile;
import com.goide.psi.GoFunctionDeclaration;
import com.goide.psi.impl.GoPackage;
import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.BlazeIntegrationTestCase;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.ideinfo.GoIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetMap;
import com.google.idea.blaze.base.ideinfo.TargetMapBuilder;
import com.google.idea.blaze.base.lang.buildfile.psi.BuildFile;
import com.google.idea.blaze.base.lang.buildfile.psi.FuncallExpression;
import com.google.idea.blaze.base.model.MockBlazeProjectDataBuilder;
import com.google.idea.blaze.base.model.MockBlazeProjectDataManager;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.plugin.PluginUtils;
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
import com.intellij.psi.util.PsiTreeUtil;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Integration tests for {@link BlazeGoImportResolver}. */
@RunWith(JUnit4.class)
public class BlazeGoImportResolverTest extends BlazeIntegrationTestCase {
  private FuncallExpression libraryFooRule;
  private FuncallExpression libraryBarGoDefaultLibraryRule;
  private PsiDirectory libraryBarDirectory;
  private PsiDirectory libraryDirectory;
  private GoFunctionDeclaration fooFunc;
  private GoFunctionDeclaration fooFooFunc;
  private GoFunctionDeclaration barFunc;

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
                    .addDependency("//library/bar:go_default_library"))
            .addTarget(
                TargetIdeInfo.builder()
                    .setBuildFile(src("library/BUILD"))
                    .setLabel("//library:foo")
                    .setKind("go_library")
                    .addSource(src("library/foo.go"))
                    .addSource(src("library/foo/foo.go"))
                    .setGoInfo(
                        GoIdeInfo.builder()
                            .addSources(
                                ImmutableList.of(src("library/foo.go"), src("library/foo/foo.go")))
                            .setImportPath("github.com/user/library/foo")))
            .addTarget(
                TargetIdeInfo.builder()
                    .setBuildFile(src("library/bar/BUILD"))
                    .setLabel("//library/bar:go_default_library")
                    .setKind("go_library")
                    .addSource(src("library/bar/bar.go"))
                    .setGoInfo(
                        GoIdeInfo.builder()
                            .addSources(ImmutableList.of(src("library/bar/bar.go")))
                            .setImportPath("github.com/user/library/bar")))
            .build();
    registerProjectService(
        BlazeProjectDataManager.class,
        new MockBlazeProjectDataManager(
            MockBlazeProjectDataBuilder.builder(workspaceRoot).setTargetMap(targetMap).build()));
    workspace.createPsiFile(
        new WorkspacePath("binary/BUILD"),
        "go_binary(",
        "    name = \"main\",",
        "    srcs = [\"main.go\"],",
        "    deps = [",
        "        \"//library:foo\",",
        "        \"//library/bar:go_default_library\",",
        "    ],",
        ")");
    BuildFile libraryBuild =
        (BuildFile)
            workspace.createPsiFile(
                new WorkspacePath("library/BUILD"),
                "go_library(",
                "    name = \"foo\",",
                "    srcs = [\"foo.go\", \"foo/foo.go\"],",
                ")");
    libraryFooRule = PsiTreeUtil.findChildOfType(libraryBuild, FuncallExpression.class);
    libraryDirectory = libraryBuild.getParent();
    assertThat(libraryDirectory).isNotNull();
    BuildFile libraryBarBuild =
        (BuildFile)
            workspace.createPsiFile(
                new WorkspacePath("library/bar/BUILD"),
                "go_library(",
                "    name = \"go_default_library\",",
                "    srcs = [\"bar.go\"],",
                ")");
    libraryBarGoDefaultLibraryRule =
        PsiTreeUtil.findChildOfType(libraryBarBuild, FuncallExpression.class);
    libraryBarDirectory = libraryBarBuild.getParent();
    assertThat(libraryBarDirectory).isNotNull();
    GoFile libraryFooGoFile =
        (GoFile)
            workspace.createPsiFile(
                new WorkspacePath("library/foo.go"), "package foo", "func Foo() {}");
    fooFunc = PsiTreeUtil.findChildOfType(libraryFooGoFile, GoFunctionDeclaration.class);
    GoFile libraryFooFooGoFile =
        (GoFile)
            workspace.createPsiFile(
                new WorkspacePath("library/foo/foo.go"), "package foo", "func FooFoo() {}");
    fooFooFunc = PsiTreeUtil.findChildOfType(libraryFooFooGoFile, GoFunctionDeclaration.class);
    GoFile libraryBarBarGoFile =
        (GoFile)
            workspace.createPsiFile(
                new WorkspacePath("library/bar/bar.go"), "package bar", "func Bar() {}");
    barFunc = PsiTreeUtil.findChildOfType(libraryBarBarGoFile, GoFunctionDeclaration.class);
  }

  @Test
  public void testGoPluginEnabled() {
    assertThat(PluginUtils.isPluginEnabled("org.jetbrains.plugins.go")).isTrue();
  }

  @Test
  public void testResolveImportPathToRule() {
    configureBinary(
        "package main",
        "import \"g<caret>ithub.com/u<caret>ser/l<caret>ibrary/f<caret>oo\"",
        "func main() {}");
    assertThatCaret(0).navigatesTo(null); // github.com
    assertThatCaret(1).navigatesTo(null); // user
    assertThatCaret(2).navigatesTo(libraryDirectory); // library
    assertThatCaret(3).navigatesTo(libraryFooRule); // foo
  }

  @Test
  public void testResolveImportPathToDirectory() {
    configureBinary(
        "package main",
        "import \"g<caret>ithub.com/u<caret>ser/l<caret>ibrary/b<caret>ar\"",
        "func main() {}");
    assertThatCaret(0).navigatesTo(null); // github.com
    assertThatCaret(1).navigatesTo(null); // user
    assertThatCaret(2).navigatesTo(libraryDirectory); // library
    assertThatCaret(3).navigatesTo(libraryBarDirectory); // bar
  }

  @Test
  public void testResolveSymbols() {
    configureBinary(
        "package main",
        "import \"github.com/user/library/foo\"",
        "import \"github.com/user/library/bar\"",
        "func main() {",
        "    f<caret>oo.F<caret>oo()",
        "    foo.F<caret>ooFoo()",
        "    b<caret>ar.B<caret>ar()",
        "}");
    assertThatCaret(0).navigatesTo(libraryFooRule); // foo
    assertThatCaret(1).navigatesTo(fooFunc); // Foo()
    assertThatCaret(2).navigatesTo(fooFooFunc); // FooFoo()
    assertThatCaret(3).navigatesTo(libraryBarGoDefaultLibraryRule); // bar
    assertThatCaret(4).navigatesTo(barFunc); // Bar()
  }

  private static ArtifactLocation src(String relativePath) {
    return ArtifactLocation.builder().setRelativePath(relativePath).setIsSource(true).build();
  }

  private void configureBinary(String... contentLines) {
    GoFile fooBarBinary =
        (GoFile) workspace.createPsiFile(new WorkspacePath("binary/main.go"), contentLines);
    testFixture.configureFromExistingVirtualFile(fooBarBinary.getVirtualFile());
  }

  private interface Subject {
    void navigatesTo(PsiElement expected);
  }

  private Subject assertThatCaret(int index) {
    return expected -> {
      Editor editor = testFixture.getEditor();
      Caret caret = editor.getCaretModel().getAllCarets().get(index);
      PsiElement actual =
          GotoDeclarationAction.findTargetElement(getProject(), editor, caret.getOffset());
      if (actual instanceof PomTargetPsiElement) {
        PomTarget target = ((PomTargetPsiElement) actual).getTarget();
        if (target instanceof GoPackage) {
          actual = ((GoPackage) target).getNavigableElement();
        }
      } else if (actual != null) {
        actual = actual.getNavigationElement();
      }
      assertThat(actual).isEqualTo(expected);
    };
  }
}
