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
import com.goide.psi.GoTypeSpec;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.idea.blaze.base.BlazeIntegrationTestCase;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.ideinfo.GoIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetMap;
import com.google.idea.blaze.base.ideinfo.TargetMapBuilder;
import com.google.idea.blaze.base.io.FileOperationProvider;
import com.google.idea.blaze.base.lang.buildfile.psi.BuildFile;
import com.google.idea.blaze.base.lang.buildfile.psi.FuncallExpression;
import com.google.idea.blaze.base.lang.buildfile.psi.util.PsiUtils;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.MockBlazeProjectDataManager;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.model.primitives.WorkspaceType;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.base.sync.projectview.WorkspaceLanguageSettings;
import com.google.idea.blaze.base.sync.workspace.WorkspacePathResolverImpl;
import com.google.idea.blaze.golang.BlazeGoSupport;
import com.google.idea.common.experiments.ExperimentService;
import com.google.idea.common.experiments.MockExperimentService;
import com.intellij.codeInsight.navigation.actions.GotoDeclarationAction;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileVisitor;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import java.io.File;
import java.io.IOException;
import java.nio.file.NotLinkException;
import java.util.List;
import java.util.Map;
import org.bouncycastle.util.Strings;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Integration tests for {@link BlazeGoGotoDeclarationHandler}. */
@RunWith(JUnit4.class)
public class BlazeGoGotoDeclarationHandlerTest extends BlazeIntegrationTestCase {

  @Before
  public void init() {
    MockExperimentService experimentService = new MockExperimentService();
    experimentService.setExperiment(BlazeGoSupport.blazeGoSupportEnabled, true);
    registerApplicationComponent(ExperimentService.class, experimentService);
    registerApplicationService(FileOperationProvider.class, new MockFileOperationProvider());
  }

  @Test
  public void testResolveGoDirectories() {
    TargetMap targetMap =
        TargetMapBuilder.builder()
            .addTarget(
                TargetIdeInfo.builder()
                    .setBuildFile(src("foo/bar/BUILD"))
                    .setLabel("//foo/bar:binary")
                    .setKind("go_binary")
                    .addSource(src("foo/bar/binary.go"))
                    .addDependency("//one/two:library")
                    .setGoInfo(
                        GoIdeInfo.builder()
                            .addSources(ImmutableList.of(src("foo/bar/binary.go")))
                            .setImportPath("prefix/foo/bar/binary")))
            .addTarget(
                TargetIdeInfo.builder()
                    .setBuildFile(src("one/two/BUILD"))
                    .setLabel("//one/two:library")
                    .setKind("go_library")
                    .addSource(src("one/two/library.go"))
                    .setGoInfo(
                        GoIdeInfo.builder()
                            .addSources(ImmutableList.of(src("one/two/library.go")))
                            .setImportPath("prefix/one/two/library")))
            .build();

    BlazeProjectData projectData =
        new BlazeProjectData(
            0L,
            targetMap,
            null,
            null,
            new WorkspacePathResolverImpl(workspaceRoot),
            location -> workspaceRoot.fileForPath(new WorkspacePath(location.getRelativePath())),
            new WorkspaceLanguageSettings(WorkspaceType.GO, ImmutableSet.of(LanguageClass.GO)),
            null,
            null);
    registerProjectService(
        BlazeProjectDataManager.class, new MockBlazeProjectDataManager(projectData));

    GoFile fooBarBinary =
        (GoFile)
            workspace.createPsiFile(
                new WorkspacePath("foo/bar/binary.go"),
                "package main",
                "import \"p<caret>refix/one<caret>/<caret>two/lib<caret>rary\"",
                "func foo(a library.One<caret>Two) {}",
                "func main() {}");

    GoFile oneTwoLibrary =
        (GoFile)
            workspace.createPsiFile(
                new WorkspacePath("one/two/library.go"),
                "package library",
                "type OneTwo struct {}");
    GoTypeSpec oneTwoStruct = PsiTreeUtil.findChildOfType(oneTwoLibrary, GoTypeSpec.class);
    PsiDirectory oneTwoDirectory = oneTwoLibrary.getParent();
    assertThat(oneTwoDirectory).isNotNull();
    PsiDirectory oneDirectory = oneTwoDirectory.getParent();
    assertThat(oneDirectory).isNotNull();

    BuildFile oneTwoBUILD =
        (BuildFile)
            workspace.createPsiFile(
                new WorkspacePath("one/two/BUILD"),
                "go_library(",
                "    name = 'library',",
                "    srcs = ['library.go'],",
                ")");
    FuncallExpression oneTwoLibraryRule =
        PsiUtils.findFirstChildOfClassRecursive(oneTwoBUILD, FuncallExpression.class);

    BlazeGoRootsProvider.createGoPathSourceRoot(getProject(), projectData);

    testFixture.configureFromExistingVirtualFile(fooBarBinary.getVirtualFile());
    List<Caret> carets = testFixture.getEditor().getCaretModel().getAllCarets();
    assertThat(carets).hasSize(5);

    PsiElement gotoPrefix =
        GotoDeclarationAction.findTargetElement(
            getProject(), testFixture.getEditor(), carets.get(0).getOffset());
    assertThat(gotoPrefix).isEqualTo(oneTwoLibraryRule);
    PsiElement gotoOne =
        GotoDeclarationAction.findTargetElement(
            getProject(), testFixture.getEditor(), carets.get(1).getOffset());
    assertThat(gotoOne).isEqualTo(oneDirectory);
    PsiElement gotoTwo =
        GotoDeclarationAction.findTargetElement(
            getProject(), testFixture.getEditor(), carets.get(2).getOffset());
    assertThat(gotoTwo).isEqualTo(oneTwoDirectory);
    PsiElement gotoLibrary =
        GotoDeclarationAction.findTargetElement(
            getProject(), testFixture.getEditor(), carets.get(3).getOffset());
    assertThat(gotoLibrary).isEqualTo(oneTwoLibraryRule);
    PsiElement gotoOneTwoType =
        GotoDeclarationAction.findTargetElement(
            getProject(), testFixture.getEditor(), carets.get(4).getOffset());
    assertThat(gotoOneTwoType).isEqualTo(oneTwoStruct);
  }

  private static ArtifactLocation src(String relativePath) {
    return ArtifactLocation.builder().setRelativePath(relativePath).setIsSource(true).build();
  }

  private class MockFileOperationProvider extends FileOperationProvider {
    private final Map<VirtualFile, VirtualFile> symlinks = Maps.newHashMap();

    @Override
    public boolean exists(File file) {
      return fileSystem.findFile(file.getPath()) != null;
    }

    @Override
    public boolean isDirectory(File file) {
      VirtualFile vf = fileSystem.findFile(file.getPath());
      return vf != null && vf.isDirectory();
    }

    @Override
    public boolean mkdirs(File file) {
      return fileSystem.createDirectory(file.getPath()).isDirectory();
    }

    @Override
    public boolean isSymbolicLink(File link) {
      VirtualFile vf = fileSystem.findFile(link.getPath());
      return vf != null && !vf.isDirectory() && symlinks.containsKey(vf);
    }

    @Override
    public File readSymbolicLink(File link) throws IOException {
      if (!isSymbolicLink(link)) {
        throw new NotLinkException(link.getPath());
      }
      VirtualFile vf = fileSystem.findFile(link.getPath());
      return VfsUtil.virtualToIoFile(symlinks.get(vf));
    }

    @Override
    public void createSymbolicLink(File link, File target) {
      VirtualFile targetVF = fileSystem.findFile(target.getPath());
      try {
        VirtualFile linkVF =
            fileSystem.createFile(
                link.getPath(), Strings.fromByteArray(targetVF.contentsToByteArray()));
        symlinks.put(linkVF, targetVF);
      } catch (IOException e) {
        throw new AssertionError(e);
      }
    }

    @Override
    public void deleteRecursively(File file) {
      VfsUtil.visitChildrenRecursively(
          fileSystem.findFile(file.getPath()),
          new VirtualFileVisitor<Object>() {
            @Override
            public boolean visitFile(VirtualFile file) {
              try {
                if (!file.isDirectory()) {
                  symlinks.remove(file);
                  file.delete(null);
                }
              } catch (IOException e) {
                throw new AssertionError(e);
              }
              return true;
            }

            @Override
            public void afterChildrenVisited(VirtualFile file) {
              try {
                file.delete(null);
              } catch (IOException e) {
                throw new AssertionError(e);
              }
            }
          });
    }
  }
}
