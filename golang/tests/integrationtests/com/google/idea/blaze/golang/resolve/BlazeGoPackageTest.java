/*
 * Copyright 2019 The Bazel Authors. All rights reserved.
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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.truth.Truth.assertThat;

import com.goide.psi.GoFile;
import com.goide.psi.GoFunctionDeclaration;
import com.goide.psi.impl.imports.GoImportResolver;
import com.google.idea.blaze.base.BlazeIntegrationTestCase;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.golang.utils.MockGoImportResolver;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.extensions.LoadingOrder;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import java.util.Arrays;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Integration tests for {@link BlazeGoPackage}. */
@RunWith(JUnit4.class)
public class BlazeGoPackageTest extends BlazeIntegrationTestCase {
  private final MockGoImportResolver importResolver = new MockGoImportResolver();

  @Before
  public void init() {
    Extensions.getRootArea()
        .getExtensionPoint(GoImportResolver.EP_NAME)
        .registerExtension(importResolver, LoadingOrder.FIRST, getTestRootDisposable());

    createGoPackage(
        "foo/bar/foo",
        goFile(
            "foo/bar/foo.go", //
            "package foo",
            "func Foo() {}"),
        goFile( // same package different directory
            "foo/foo.go", //
            "package foo",
            "func Bar() {}"));
    // same directory different package
    createGoPackage(
        "foo/bar/bar",
        goFile(
            "foo/bar/bar.go", //
            "package bar",
            "func Foo() {}"));
    // same directory same package different import path
    createGoPackage(
        "foo/bar/baz",
        goFile(
            "foo/bar/baz.go", //
            "package foo",
            "func Foo() {}"));
  }

  @Test
  public void testResolve() {
    GoFile main =
        goFile(
            "main/main.go", //
            "package main",
            "import \"foo/bar/foo\"",
            "func main() {",
            "   foo.F<caret>oo()",
            "}");
    createGoPackage("main/main", main);
    testFixture.configureFromExistingVirtualFile(main.getVirtualFile());
    PsiElement element = testFixture.getElementAtCaret();
    assertThat(element).isInstanceOf(GoFunctionDeclaration.class);
    GoFunctionDeclaration function = (GoFunctionDeclaration) element;
    assertThat(function.getName()).isEqualTo("Foo");
    assertThat(containingFile(function)).isEqualTo("/src/workspace/foo/bar/foo.go");
  }

  @Test
  public void testSamePackageDifferentDirectories() {
    GoFile main =
        goFile(
            "main/main.go", //
            "package main",
            "import \"foo/bar/foo\"",
            "func main() {",
            "   foo.Foo()",
            "   foo.B<caret>ar()",
            "}");
    createGoPackage("main/main", main);
    testFixture.configureFromExistingVirtualFile(main.getVirtualFile());
    PsiElement element = testFixture.getElementAtCaret();
    assertThat(element).isInstanceOf(GoFunctionDeclaration.class);
    GoFunctionDeclaration function = (GoFunctionDeclaration) element;
    assertThat(function.getName()).isEqualTo("Bar");
    assertThat(containingFile(function)).isEqualTo("/src/workspace/foo/foo.go");
  }

  @Test
  public void testSameDirectoryDifferentPackage() {
    GoFile main =
        goFile(
            "main/main.go", //
            "package main",
            "import \"foo/bar/foo\"",
            "import \"foo/bar/bar\"",
            "func main() {",
            "   foo.Foo()",
            "   bar.F<caret>oo()",
            "}");
    createGoPackage("main/main", main);
    testFixture.configureFromExistingVirtualFile(main.getVirtualFile());
    PsiElement element = testFixture.getElementAtCaret();
    assertThat(element).isInstanceOf(GoFunctionDeclaration.class);
    GoFunctionDeclaration function = (GoFunctionDeclaration) element;
    assertThat(function.getName()).isEqualTo("Foo");
    assertThat(containingFile(function)).isEqualTo("/src/workspace/foo/bar/bar.go");
  }

  @Test
  public void testSamePackageNameDifferentImportPath() {
    GoFile main =
        goFile(
            "main/main.go", //
            "package main",
            "import \"foo/bar/foo\"",
            "import \"foo/bar/bar\"",
            "import foo2 \"foo/bar/baz\"",
            "func main() {",
            "   foo.Foo()",
            "   foo2.F<caret>oo()",
            "}");
    createGoPackage("main/main", main);
    testFixture.configureFromExistingVirtualFile(main.getVirtualFile());
    PsiElement element = testFixture.getElementAtCaret();
    assertThat(element).isInstanceOf(GoFunctionDeclaration.class);
    GoFunctionDeclaration function = (GoFunctionDeclaration) element;
    assertThat(function.getName()).isEqualTo("Foo");
    assertThat(containingFile(function)).isEqualTo("/src/workspace/foo/bar/baz.go");
  }

  private GoFile goFile(String relativePath, String... contentLines) {
    return (GoFile) workspace.createPsiFile(new WorkspacePath(relativePath), contentLines);
  }

  private static String containingFile(PsiElement element) {
    return element.getContainingFile().getVirtualFile().getPath();
  }

  private void createGoPackage(String importPath, GoFile... files) {
    int lastSlash = importPath.lastIndexOf('/');
    Label label =
        Label.create(
            "//" + importPath.substring(0, lastSlash) + ':' + importPath.substring(lastSlash + 1));
    importResolver.put(
        importPath,
        new BlazeGoPackage(
            getProject(),
            importPath,
            label,
            Arrays.stream(files)
                .map(PsiFile::getVirtualFile)
                .map(VfsUtil::virtualToIoFile)
                .collect(toImmutableList())));
  }

}
