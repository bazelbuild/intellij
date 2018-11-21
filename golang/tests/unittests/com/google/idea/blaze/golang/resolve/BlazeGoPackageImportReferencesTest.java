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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.idea.blaze.base.BlazeTestCase;
import com.google.idea.blaze.base.lang.buildfile.psi.BuildFile;
import com.google.idea.blaze.base.lang.buildfile.psi.FuncallExpression;
import com.google.idea.blaze.base.model.primitives.Label;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link BlazeGoPackage#getImportReferences}. */
@RunWith(JUnit4.class)
public class BlazeGoPackageImportReferencesTest extends BlazeTestCase {
  @Test
  public void testFromBuildFile() {
    Label label = Label.create("//foo/bar:baz");
    PsiDirectory workspaceDirectory = mockPsiDirectory("workspace", null);
    PsiDirectory fooDirectory = mockPsiDirectory("foo", workspaceDirectory);
    PsiDirectory fooBarDirectory = mockPsiDirectory("bar", fooDirectory);
    BuildFile fooBarBuild = mockBuildFile(fooBarDirectory);
    PsiElement[] importReferences =
        BlazeGoPackage.getImportReferences(label, fooBarBuild, "foo/bar/baz");
    assertThat(importReferences)
        .isEqualTo(
            new PsiElement[] {
              fooDirectory, // foo
              fooBarDirectory, // bar
              fooBarBuild, // baz
            });
  }

  @Test
  public void testFromRule() {
    Label label = Label.create("//foo/bar:baz");
    PsiDirectory workspaceDirectory = mockPsiDirectory("workspace", null);
    PsiDirectory fooDirectory = mockPsiDirectory("foo", workspaceDirectory);
    PsiDirectory fooBarDirectory = mockPsiDirectory("bar", fooDirectory);
    BuildFile fooBarBuild = mockBuildFile(fooBarDirectory);
    FuncallExpression fooBarBazRule = mockRule("baz", fooBarBuild);
    PsiElement[] importReferences =
        BlazeGoPackage.getImportReferences(label, fooBarBazRule, "foo/bar/baz");
    assertThat(importReferences)
        .isEqualTo(
            new PsiElement[] {
              fooDirectory, // foo
              fooBarDirectory, // bar
              fooBarBazRule, // baz
            });
  }

  @Test
  public void testFromDefaultLibraryBuildFile() {
    Label label = Label.create("//foo/bar/baz:go_default_library");
    PsiDirectory workspaceDirectory = mockPsiDirectory("workspace", null);
    PsiDirectory fooDirectory = mockPsiDirectory("foo", workspaceDirectory);
    PsiDirectory fooBarDirectory = mockPsiDirectory("bar", fooDirectory);
    PsiDirectory fooBarBazDirectory = mockPsiDirectory("baz", fooBarDirectory);
    BuildFile fooBarBazBuild = mockBuildFile(fooBarBazDirectory);
    PsiElement[] importReferences =
        BlazeGoPackage.getImportReferences(label, fooBarBazBuild, "foo/bar/baz");
    assertThat(importReferences)
        .isEqualTo(
            new PsiElement[] {
              fooDirectory, // foo
              fooBarDirectory, // bar
              fooBarBazDirectory, // baz
            });
  }

  @Test
  public void testFromDefaultLibraryRule() {
    Label label = Label.create("//foo/bar/baz:go_default_library");
    PsiDirectory workspaceDirectory = mockPsiDirectory("workspace", null);
    PsiDirectory fooDirectory = mockPsiDirectory("foo", workspaceDirectory);
    PsiDirectory fooBarDirectory = mockPsiDirectory("bar", fooDirectory);
    PsiDirectory fooBarBazDirectory = mockPsiDirectory("baz", fooBarDirectory);
    BuildFile fooBarBazBuild = mockBuildFile(fooBarBazDirectory);
    FuncallExpression fooBarBazGoDefaultLibraryRule =
        mockRule("go_default_library", fooBarBazBuild);
    PsiElement[] importReferences =
        BlazeGoPackage.getImportReferences(label, fooBarBazGoDefaultLibraryRule, "foo/bar/baz");
    assertThat(importReferences)
        .isEqualTo(
            new PsiElement[] {
              fooDirectory, // foo
              fooBarDirectory, // bar
              fooBarBazDirectory, // baz
            });
  }

  @Test
  public void testResolvableImportPrefix() {
    Label label = Label.create("//foo/bar:baz");
    PsiDirectory externalGithubDirectory = mockPsiDirectory("github.com", null);
    PsiDirectory externalGithubUserDirectory = mockPsiDirectory("user", externalGithubDirectory);
    PsiDirectory fooDirectory = mockPsiDirectory("foo", externalGithubUserDirectory);
    PsiDirectory fooBarDirectory = mockPsiDirectory("bar", fooDirectory);
    BuildFile fooBarBuild = mockBuildFile(fooBarDirectory);
    FuncallExpression fooBarBazRule = mockRule("baz", fooBarBuild);
    PsiElement[] importReferences =
        BlazeGoPackage.getImportReferences(label, fooBarBazRule, "github.com/user/foo/bar/baz");
    assertThat(importReferences)
        .isEqualTo(
            new PsiElement[] {
              externalGithubDirectory, // github.com
              externalGithubUserDirectory, // user
              fooDirectory, // foo
              fooBarDirectory, // bar
              fooBarBazRule, // baz
            });
  }

  @Test
  public void testUnresolvableImportPrefix() {
    Label label = Label.create("//foo/bar:baz");
    PsiDirectory externalHomeDirectory = mockPsiDirectory("home", null);
    PsiDirectory externalHomeProjectDirectory = mockPsiDirectory("project", externalHomeDirectory);
    PsiDirectory fooDirectory = mockPsiDirectory("foo", externalHomeProjectDirectory);
    PsiDirectory fooBarDirectory = mockPsiDirectory("bar", fooDirectory);
    BuildFile fooBarBuild = mockBuildFile(fooBarDirectory);
    FuncallExpression fooBarBazRule = mockRule("baz", fooBarBuild);
    PsiElement[] importReferences =
        BlazeGoPackage.getImportReferences(label, fooBarBazRule, "github.com/user/foo/bar/baz");
    assertThat(importReferences)
        .isEqualTo(
            new PsiElement[] {
              null, // github.com
              null, // user
              fooDirectory, // foo
              fooBarDirectory, // bar
              fooBarBazRule, // baz
            });
  }

  @Test
  public void testStopsAtRootDirectory() {
    Label label = Label.create("//foo/bar:baz");
    PsiDirectory fooDirectory = mockPsiDirectory("foo", null);
    PsiDirectory fooBarDirectory = mockPsiDirectory("bar", fooDirectory);
    BuildFile fooBarBuild = mockBuildFile(fooBarDirectory);
    FuncallExpression fooBarBazRule = mockRule("baz", fooBarBuild);
    PsiElement[] importReferences =
        BlazeGoPackage.getImportReferences(label, fooBarBazRule, "github.com/user/foo/bar/baz");
    assertThat(importReferences)
        .isEqualTo(
            new PsiElement[] {
              null, // github.com
              null, // user
              fooDirectory, // foo
              fooBarDirectory, // bar
              fooBarBazRule, // baz
            });
  }

  @Test
  public void testUnrelatedImportPath() {
    Label label = Label.create("//foo/bar:baz");
    PsiDirectory externalHomeDirectory = mockPsiDirectory("home", null);
    PsiDirectory externalHomeProjectDirectory = mockPsiDirectory("project", externalHomeDirectory);
    PsiDirectory fooDirectory = mockPsiDirectory("foo", externalHomeProjectDirectory);
    PsiDirectory fooBarDirectory = mockPsiDirectory("bar", fooDirectory);
    BuildFile fooBarBuild = mockBuildFile(fooBarDirectory);
    FuncallExpression fooBarBazRule = mockRule("baz", fooBarBuild);
    PsiElement[] importReferences =
        BlazeGoPackage.getImportReferences(label, fooBarBazRule, "one/two/three");
    assertThat(importReferences)
        .isEqualTo(
            new PsiElement[] {
              null, // one
              null, // two
              null, // three
            });
  }

  private static PsiDirectory mockPsiDirectory(String name, PsiDirectory parent) {
    PsiDirectory directory = mock(PsiDirectory.class);
    when(directory.getName()).thenReturn(name);
    when(directory.getParent()).thenReturn(parent);
    when(directory.getParentDirectory()).thenReturn(parent);
    return directory;
  }

  private static BuildFile mockBuildFile(PsiDirectory parent) {
    BuildFile buildFile = mock(BuildFile.class);
    when(buildFile.getName()).thenReturn("BUILD");
    when(buildFile.getParent()).thenReturn(parent);
    when(buildFile.getContainingFile()).thenReturn(buildFile);
    return buildFile;
  }

  private static FuncallExpression mockRule(String name, BuildFile buildFile) {
    FuncallExpression rule = mock(FuncallExpression.class);
    when(rule.getName()).thenReturn(name);
    when(rule.getParent()).thenReturn(buildFile);
    when(rule.getContainingFile()).thenReturn(buildFile);
    return rule;
  }
}
