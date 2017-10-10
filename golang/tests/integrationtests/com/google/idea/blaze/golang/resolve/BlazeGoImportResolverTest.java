/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
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
import com.goide.psi.GoImportSpec;
import com.goide.psi.GoTypeReferenceExpression;
import com.goide.psi.GoTypeSpec;
import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.BlazeIntegrationTestCase;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.ideinfo.GoIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetMap;
import com.google.idea.blaze.base.ideinfo.TargetMapBuilder;
import com.google.idea.blaze.base.lang.buildfile.psi.util.PsiUtils;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.MockBlazeProjectDataManager;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.plugin.PluginUtils;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.base.sync.workspace.WorkspacePathResolverImpl;
import com.google.idea.blaze.golang.BlazeGoSupport;
import com.google.idea.common.experiments.ExperimentService;
import com.google.idea.common.experiments.MockExperimentService;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Before;
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
  }

  @Test
  public void testGoPluginEnabled() {
    assertThat(PluginUtils.isPluginEnabled("org.jetbrains.plugins.go")).isTrue();
  }

  @Test
  public void testResolveGoPackage() {
    TargetMap targetMap =
        TargetMapBuilder.builder()
            .addTarget(
                TargetIdeInfo.builder()
                    .setBuildFile(src("foo/bar/BUILD"))
                    .setLabel("//foo/bar:binary")
                    .setKind("go_binary")
                    .addSource(src("foo/bar/binary.go"))
                    .addDependency("//one/two:library"))
            .addTarget(
                TargetIdeInfo.builder()
                    .setBuildFile(src("one/two/BUILD"))
                    .setLabel("//one/two:library")
                    .setKind("go_library")
                    .addSource(src("one/two/library.go"))
                    .addSource(src("one/two/three/library.go"))
                    .setGoInfo(
                        GoIdeInfo.builder()
                            .addSources(
                                ImmutableList.of(
                                    src("one/two/library.go"), src("one/two/three/library.go")))
                            .setImportPath("prefix/one/two/library")))
            .build();

    registerProjectService(
        BlazeProjectDataManager.class,
        new MockBlazeProjectDataManager(
            new BlazeProjectData(
                0L,
                targetMap,
                null,
                null,
                new WorkspacePathResolverImpl(workspaceRoot),
                location ->
                    workspaceRoot.fileForPath(new WorkspacePath(location.getRelativePath())),
                null,
                null,
                null)));

    GoFile fooBarBinary =
        (GoFile)
            workspace.createPsiFile(
                new WorkspacePath("foo/bar/binary.go"),
                "package main",
                "import \"prefix/one/two/library\"",
                "func foo(a library.OneTwo, b library.OneTwoThree) {}",
                "func main() {}");

    GoFile oneTwoLibrary =
        (GoFile)
            workspace.createPsiFile(
                new WorkspacePath("one/two/library.go"),
                "package library",
                "type OneTwo struct {}");
    GoFile oneTwoThreeLibrary =
        (GoFile)
            workspace.createPsiFile(
                new WorkspacePath("one/two/three/library.go"),
                "package library",
                "type OneTwoThree struct {}");

    GoImportSpec importSpec =
        PsiUtils.findFirstChildOfClassRecursive(fooBarBinary, GoImportSpec.class);
    assertThat(importSpec).isNotNull();

    PsiDirectory library = importSpec.resolve();
    assertThat(library).isNotNull();
    assertThat(library.isValid()).isTrue();
    assertThat(library.getName()).isEqualTo("library");
    assertThat(library.getChildren()).asList().containsExactly(oneTwoLibrary, oneTwoThreeLibrary);

    BlazeVirtualGoDirectory root = BlazeGoRootsProvider.getGoPathSourceRoot(getProject());
    assertThat(root.getPath()).isEmpty();
    assertThat(root.getChildren()).hasLength(1);

    BlazeVirtualGoDirectory prefix = (BlazeVirtualGoDirectory) root.findChild("prefix");
    assertThat(prefix).isNotNull();
    assertThat(prefix.getPath()).isEqualTo("/prefix");
    assertThat(prefix.getParent()).isEqualTo(root);
    assertThat(prefix.getChildren()).hasLength(1);

    BlazeVirtualGoDirectory prefixOne = (BlazeVirtualGoDirectory) prefix.findChild("one");
    assertThat(prefixOne).isNotNull();
    assertThat(prefixOne.getPath()).isEqualTo("/prefix/one");
    assertThat(prefixOne.getParent()).isEqualTo(prefix);
    assertThat(prefixOne.getChildren()).hasLength(1);

    BlazeVirtualGoDirectory prefixOneTwo = (BlazeVirtualGoDirectory) prefixOne.findChild("two");
    assertThat(prefixOneTwo).isNotNull();
    assertThat(prefixOneTwo.getPath()).isEqualTo("/prefix/one/two");
    assertThat(prefixOneTwo.getParent()).isEqualTo(prefixOne);
    assertThat(prefixOneTwo.getChildren()).hasLength(1);

    BlazeVirtualGoPackage prefixOneTwoLibrary =
        (BlazeVirtualGoPackage) prefixOneTwo.findChild("library");
    assertThat(prefixOneTwoLibrary).isNotNull();
    assertThat(prefixOneTwoLibrary.getPath()).isEqualTo("/prefix/one/two/library");
    assertThat(prefixOneTwoLibrary.getParent()).isEqualTo(prefixOneTwo);
    assertThat(prefixOneTwoLibrary.getChildren())
        .asList()
        .containsExactly(oneTwoLibrary.getVirtualFile(), oneTwoThreeLibrary.getVirtualFile());
    assertThat(PsiManager.getInstance(getProject()).findDirectory(prefixOneTwoLibrary))
        .isEqualTo(library);

    List<GoTypeReferenceExpression> typeReferences =
        PsiUtils.findAllChildrenOfClassRecursive(fooBarBinary, GoTypeReferenceExpression.class);
    List<PsiElement> resolvedReferences =
        typeReferences
            .stream()
            .map(GoTypeReferenceExpression::resolve)
            .filter(e -> e instanceof GoTypeSpec)
            .collect(Collectors.toList());
    GoTypeSpec oneTwoTypeSpec =
        PsiUtils.findFirstChildOfClassRecursive(oneTwoLibrary, GoTypeSpec.class);
    GoTypeSpec oneTwoThreeTypeSPec =
        PsiUtils.findFirstChildOfClassRecursive(oneTwoThreeLibrary, GoTypeSpec.class);
    assertThat(resolvedReferences).containsExactly(oneTwoTypeSpec, oneTwoThreeTypeSPec);
  }

  private static ArtifactLocation src(String relativePath) {
    return ArtifactLocation.builder().setRelativePath(relativePath).setIsSource(true).build();
  }
}
