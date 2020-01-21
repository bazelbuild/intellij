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
package com.google.idea.blaze.golang.resolve;

import static com.google.common.truth.Truth.assertThat;

import com.goide.highlighting.GoAnnotator;
import com.goide.psi.GoFile;
import com.google.idea.blaze.base.BlazeIntegrationTestCase;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.ideinfo.GoIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetMapBuilder;
import com.google.idea.blaze.base.model.MockBlazeProjectDataBuilder;
import com.google.idea.blaze.base.model.MockBlazeProjectDataManager;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.intellij.codeInsight.daemon.impl.AnnotationHolderImpl;
import com.intellij.lang.annotation.AnnotationSession;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Integration tests for how {@link GoAnnotator} interacts with blaze projects. */
@RunWith(JUnit4.class)
public class BlazeGoAnnotatorTest extends BlazeIntegrationTestCase {
  @Before
  public void init() {
    registerProjectService(
        BlazeProjectDataManager.class,
        new MockBlazeProjectDataManager(
            MockBlazeProjectDataBuilder.builder(workspaceRoot)
                .setTargetMap(
                    TargetMapBuilder.builder()
                        .addTarget(
                            TargetIdeInfo.builder()
                                .setBuildFile(src("foo/BUILD"))
                                .setLabel("//foo:foo")
                                .setKind("go_library")
                                .addSource(src("foo/foo.go"))
                                .addSource(src("foo/bar/foo.go"))
                                .setGoInfo(
                                    GoIdeInfo.builder()
                                        .addSource(src("foo/foo.go"))
                                        .addSource(src("foo/bar/foo.go"))
                                        .setImportPath("foo/foo")))
                        .build())
                .build()));
  }

  @Ignore("until https://youtrack.jetbrains.com/issue/GO-8698 is fixed")
  @Test
  public void testSamePackageDifferentDirectoryUnexportedSymbols() {
    psi(
        "foo/BUILD", //
        "go_library(",
        "    name = \"foo\",",
        "    srcs = [\"foo.go\"],",
        "    srcs = [\"foo/bar/foo.go\"],",
        ")");
    psi(
        "foo/bar/foo.go", //
        "package foo",
        "func bar() {}");
    GoFile foo =
        (GoFile)
            psi(
                "foo/foo.go", //
                "package foo",
                "func foo() {",
                "  b<caret>ar()",
                "}");
    testFixture.configureFromExistingVirtualFile(foo.getVirtualFile());
    PsiReference reference = foo.findReferenceAt(testFixture.getCaretOffset());
    assertThat(reference).isNotNull();
    PsiElement element = reference.getElement();
    AnnotationHolderImpl holder = new AnnotationHolderImpl(new AnnotationSession(foo));
    new GoAnnotator().annotate(element, holder);
    assertThat(holder).isEmpty();
  }

  private PsiFile psi(String relativePath, String... contentLines) {
    return workspace.createPsiFile(new WorkspacePath(relativePath), contentLines);
  }

  private static ArtifactLocation src(String relativePath) {
    return ArtifactLocation.builder().setRelativePath(relativePath).setIsSource(true).build();
  }
}
