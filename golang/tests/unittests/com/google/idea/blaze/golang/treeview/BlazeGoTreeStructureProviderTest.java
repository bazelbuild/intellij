/*
 * Copyright 2022 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.golang.treeview;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.truth.Truth.assertThat;
import static com.google.idea.blaze.golang.sync.BlazeGoAdditionalLibraryRootsProvider.GO_EXTERNAL_LIBRARY_ROOT_NAME;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.io.VirtualFileSystemProvider;
import com.google.idea.blaze.base.settings.BlazeImportSettings;
import com.google.idea.blaze.base.settings.BlazeImportSettings.ProjectType;
import com.google.idea.blaze.base.settings.BlazeImportSettingsManager;
import com.google.idea.blaze.base.settings.BuildSystemName;
import com.google.idea.blaze.base.sync.SyncCache;
import com.google.idea.blaze.base.sync.libraries.BlazeExternalSyntheticLibrary;
import com.google.idea.blaze.golang.resolve.BlazeGoPackageFactory;
import com.google.idea.testing.IntellijRule;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.nodes.PsiFileNode;
import com.intellij.ide.projectView.impl.nodes.SyntheticLibraryElementNode;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.ide.util.treeView.TreeAnchorizer;
import com.intellij.lang.FileASTNode;
import com.intellij.mock.MockLocalFileSystem;
import com.intellij.mock.MockVirtualFile;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.FakePsiElement;
import com.intellij.psi.search.PsiElementProcessor;
import java.io.File;
import java.util.Collection;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

/** Unit tests for {@link BlazeGoTreeStructureProvider} */
@RunWith(JUnit4.class)
public class BlazeGoTreeStructureProviderTest {
  @Rule public final MockitoRule mockito = MockitoJUnit.rule();
  @Rule public IntellijRule intellij = new IntellijRule();
  @Rule public TemporaryFolder folder = new TemporaryFolder();
  private static final BlazeImportSettings DUMMY_IMPORT_SETTINGS =
      new BlazeImportSettings("", "", "", "", BuildSystemName.Blaze, ProjectType.ASPECT_SYNC);
  @Mock private SyncCache syncCache;

  private ConcurrentHashMap<File, String> fileToImportPathMap;
  private SyntheticLibraryElementNode rootNode;

  @Before
  public void setUp() {
    intellij.registerApplicationService(TreeAnchorizer.class, new FakeTreeAnchorizer());

    intellij.registerProjectService(
        BlazeImportSettingsManager.class, new BlazeImportSettingsManager(intellij.getProject()));

    intellij.registerApplicationService(VirtualFileSystemProvider.class, MockLocalFileSystem::new);
    intellij.registerProjectService(SyncCache.class, syncCache);

    fileToImportPathMap = new ConcurrentHashMap<>();
    when(syncCache.get(eq(BlazeGoPackageFactory.class), any())).thenReturn(fileToImportPathMap);

    BlazeImportSettingsManager.getInstance(intellij.getProject())
        .setImportSettings(DUMMY_IMPORT_SETTINGS);

    rootNode = createRootNode(GO_EXTERNAL_LIBRARY_ROOT_NAME);
  }

  @Test
  public void givenDifferentImportPaths_createTwoRootNodes() {
    // GIVEN
    String fileName1 = "bar.go";
    VirtualFile file1 = MockVirtualFile.file(fileName1);
    PsiFileNode fileNode1 = createPsiFileNode(file1);
    fileToImportPathMap.put(VfsUtil.virtualToIoFile(file1), "root1");

    String fileName2 = "buzz.go";
    VirtualFile file2 = MockVirtualFile.file(fileName2);
    PsiFileNode fileNode2 = createPsiFileNode(file2);
    fileToImportPathMap.put(VfsUtil.virtualToIoFile(file2), "root2");

    // WHEN
    ImmutableList<GoSyntheticLibraryElementNode> actualChildren =
        new BlazeGoTreeStructureProvider()
                .modify(
                    /* parent= */ rootNode,
                    /* children= */ ImmutableList.of(fileNode1, fileNode2),
                    /* settings= */ new ViewSettings() {})
                .stream()
                .map(GoSyntheticLibraryElementNode.class::cast)
                .collect(toImmutableList());

    // THEN
    assertThat(actualChildren).hasSize(2);
    assertThat(actualChildren.get(0).getName()).isEqualTo("root1");
    assertThat(actualChildren.get(1).getName()).isEqualTo("root2");

    ImmutableList<AbstractTreeNode<?>> secondLevelImportPath1 =
        ImmutableList.copyOf(actualChildren.get(0).getChildren());
    assertThat(secondLevelImportPath1).containsExactly(fileNode1);

    ImmutableList<AbstractTreeNode<?>> secondLevelImportPath2 =
        ImmutableList.copyOf(actualChildren.get(1).getChildren());
    assertThat(secondLevelImportPath2).containsExactly(fileNode2);
  }

  @Test
  public void givenNestedImportPaths_createMultiLevelStructure() {
    // GIVEN
    String fileName1 = "bar.go";
    VirtualFile file1 = MockVirtualFile.file(fileName1);
    PsiFileNode fileNode1 = createPsiFileNode(file1);
    fileToImportPathMap.put(VfsUtil.virtualToIoFile(file1), "root");

    String fileName2 = "buzz.go";
    VirtualFile file2 = MockVirtualFile.file(fileName2);
    PsiFileNode fileNode2 = createPsiFileNode(file2);
    fileToImportPathMap.put(VfsUtil.virtualToIoFile(file2), "root/someOtherFolder");

    // WHEN
    ImmutableList<GoSyntheticLibraryElementNode> actualChildren =
        new BlazeGoTreeStructureProvider()
                .modify(
                    /* parent= */ rootNode,
                    /* children= */ ImmutableList.of(fileNode1, fileNode2),
                    /* settings= */ new ViewSettings() {})
                .stream()
                .map(GoSyntheticLibraryElementNode.class::cast)
                .collect(toImmutableList());

    // THEN
    assertThat(actualChildren).hasSize(1);
    assertThat(actualChildren.get(0).getName()).isEqualTo("root");

    ImmutableList<AbstractTreeNode<?>> secondLevel =
        ImmutableList.copyOf(actualChildren.get(0).getChildren());
    assertThat(secondLevel).hasSize(2);
    assertThat(secondLevel.get(0)).isEqualTo(fileNode1);
    assertThat(secondLevel.get(1).getName()).isEqualTo("someOtherFolder");

    ImmutableList<AbstractTreeNode<?>> thirdLevel =
        ImmutableList.copyOf(secondLevel.get(1).getChildren());
    assertThat(thirdLevel).containsExactly(fileNode2);
  }

  @Test
  public void givenFilesNotInToImportPathMap_attachAtRootLevel() {
    String fileName1 = "bar.go";
    VirtualFile file1 = MockVirtualFile.file(fileName1);
    PsiFileNode fileNode1 = createPsiFileNode(file1);

    String fileName2 = "bar.go";
    VirtualFile file2 = MockVirtualFile.file(fileName2);
    PsiFileNode fileNode2 = createPsiFileNode(file2);

    ImmutableList<AbstractTreeNode<?>> actualChildren =
        ImmutableList.copyOf(
            new BlazeGoTreeStructureProvider()
                .modify(
                    /* parent= */ rootNode,
                    /* children= */ ImmutableList.of(fileNode1, fileNode2),
                    /* settings= */ new ViewSettings() {}));

    assertThat(actualChildren).containsExactly(fileNode1, fileNode2);
  }

  @Test
  public void givenSingleNode_addSourceRootsToLibrary() {
    String fileName = "bar.go";
    VirtualFile file = MockVirtualFile.file(fileName);
    PsiFileNode fileNode = createPsiFileNode(file);
    fileToImportPathMap.put(VfsUtil.virtualToIoFile(file), "root");

    ImmutableList<AbstractTreeNode<?>> actualChildren =
        ImmutableList.copyOf(
            new BlazeGoTreeStructureProvider()
                .modify(
                    /* parent= */ rootNode,
                    /* children= */ ImmutableList.of(fileNode),
                    /* settings= */ new ViewSettings() {}));

    assertThat(actualChildren).hasSize(1);
    assertThat(actualChildren.get(0)).isInstanceOf(GoSyntheticLibraryElementNode.class);
    assertThat(
            ((BlazeGoExternalSyntheticLibrary) actualChildren.get(0).getValue()).getSourceRoots())
        .containsExactly(file);
  }

  @Test
  public void givenWrongRootNode_doNothing() {
    String fileName = "bar.go";
    VirtualFile file = MockVirtualFile.file(fileName);
    PsiFileNode fileNode = createPsiFileNode(file);
    fileToImportPathMap.put(VfsUtil.virtualToIoFile(file), "root");

    Collection<AbstractTreeNode<?>> actualChildren =
        new BlazeGoTreeStructureProvider()
            .modify(
                /* parent= */ createRootNode("Wrong root node"),
                /* children= */ ImmutableList.of(fileNode),
                /* settings= */ new ViewSettings() {});

    assertThat(actualChildren).containsExactly(fileNode);
  }

  @Test
  public void givenNotInBlazeProject_doNothing() {
    BlazeImportSettingsManager.getInstance(intellij.getProject()).setImportSettings(null);
    String fileName = "bar.go";
    VirtualFile file = MockVirtualFile.file(fileName);
    PsiFileNode fileNode = createPsiFileNode(file);
    fileToImportPathMap.put(VfsUtil.virtualToIoFile(file), "root");

    Collection<AbstractTreeNode<?>> actualChildren =
        new BlazeGoTreeStructureProvider()
            .modify(
                /* parent= */ rootNode,
                /* children= */ ImmutableList.of(fileNode),
                /* settings= */ new ViewSettings() {});

    assertThat(actualChildren).containsExactly(fileNode);
  }

  @Test
  public void givenNodeNotInOriginalList_doNothing() {
    String fileName = "bar.go";
    VirtualFile file = MockVirtualFile.file(fileName);
    fileToImportPathMap.put(VfsUtil.virtualToIoFile(file), "root");

    Collection<AbstractTreeNode<?>> actualChildren =
        new BlazeGoTreeStructureProvider()
            .modify(
                /* parent= */ rootNode,
                /* children= */ ImmutableList.of(),
                /* settings= */ new ViewSettings() {});

    assertThat(actualChildren).isEmpty();
  }

  private PsiFileNode createPsiFileNode(VirtualFile virtualFile) {
    return new PsiFileNode(
        intellij.getProject(),
        new FakePsiFile(intellij.getProject(), virtualFile),
        new ViewSettings() {});
  }

  private SyntheticLibraryElementNode createRootNode(String nodeName) {
    BlazeExternalSyntheticLibrary parentLibrary =
        new BlazeExternalSyntheticLibrary(
            /* presentableText= */ nodeName, /* files= */ ImmutableList.of());

    return new SyntheticLibraryElementNode(
        intellij.getProject(), parentLibrary, parentLibrary, new ViewSettings() {});
  }

  private static final class FakeTreeAnchorizer extends TreeAnchorizer {
    @Override
    public Object createAnchor(Object element) {
      return element;
    }

    private FakeTreeAnchorizer() {}
  }

  private abstract static class FakePsiFileSystemItem extends FakePsiElement
      implements PsiFileSystemItem {
    final Project project;
    final VirtualFile vf;

    private FakePsiFileSystemItem(Project project, VirtualFile vf) {
      this.project = project;
      this.vf = vf;
    }

    @Override
    public VirtualFile getVirtualFile() {
      return vf;
    }

    @Override
    public Project getProject() {
      return project;
    }

    @Override
    public String toString() {
      return vf.getPath();
    }

    @Override
    public boolean isValid() {
      return true;
    }

    @Override
    public boolean canNavigate() {
      return false;
    }

    @Override
    public boolean canNavigateToSource() {
      return false;
    }

    @Override
    public void navigate(boolean requestFocus) {}

    @Override
    public boolean processChildren(PsiElementProcessor<? super PsiFileSystemItem> processor) {
      return false;
    }

    @Override
    public void checkSetName(String name) {}
  }

  private static final class FakePsiFile extends FakePsiFileSystemItem implements PsiFile {

    private FakePsiFile(Project project, VirtualFile vf) {
      super(project, vf);
    }

    @Nullable
    @Override
    public FileASTNode getNode() {
      return null;
    }

    @Override
    @Nullable
    public PsiDirectory getParent() {
      return null;
    }

    @Override
    public PsiFile getContainingFile() {
      return this;
    }

    @Override
    public PsiDirectory getContainingDirectory() {
      return null;
    }

    @Override
    public boolean isDirectory() {
      return false;
    }

    @Override
    public long getModificationStamp() {
      return 0;
    }

    @Override
    public PsiFile getOriginalFile() {
      return this;
    }

    @Override
    public FileType getFileType() {
      return FileTypeManager.getInstance().getFileTypeByFileName(vf.getName());
    }

    @Override
    public PsiFile[] getPsiRoots() {
      return PsiFile.EMPTY_ARRAY;
    }

    @Override
    public FileViewProvider getViewProvider() {
      return Objects.requireNonNull(PsiManager.getInstance(project).findViewProvider(vf));
    }

    @Override
    public void subtreeChanged() {}
  }
}
