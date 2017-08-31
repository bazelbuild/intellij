/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.android.npw.project;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.android.builder.model.SourceProvider;
import com.android.tools.idea.npw.project.AndroidProjectPaths;
import com.android.tools.idea.npw.project.AndroidSourceSet;
import com.android.tools.idea.project.BuildSystemService;
import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.android.project.BlazeBuildSystemService;
import com.google.idea.blaze.android.sync.model.idea.SourceProviderImpl;
import com.google.idea.blaze.base.BlazeTestCase;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.BlazeImportSettings;
import com.google.idea.blaze.base.settings.BlazeImportSettingsManager;
import com.intellij.facet.FacetType;
import com.intellij.facet.FacetTypeRegistry;
import com.intellij.facet.impl.FacetTypeRegistryImpl;
import com.intellij.mock.MockModule;
import com.intellij.mock.MockVirtualFile;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaDirectoryService;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.impl.file.PsiPackageImpl;
import java.io.File;
import java.util.List;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.AndroidFacetConfiguration;
import org.jetbrains.android.facet.AndroidFacetType;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Test cases for {@link BlazeAndroidProjectPaths}. */
@RunWith(JUnit4.class)
public class BlazeAndroidProjectPathsTest extends BlazeTestCase {
  private VirtualFile root = new MockVirtualFile(true, "root");
  private VirtualFile resource = new MockVirtualFile(true, "root/resource");
  private VirtualFile target = new MockVirtualFile(true, "root/library/com/google/target");

  @Override
  protected void initTest(Container applicationServices, Container projectServices) {
    mockFacetRegistry(applicationServices);
    mockBlazeImportSettings(projectServices);
    mockPsiPackage(applicationServices, projectServices);

    registerExtensionPoint(
            ExtensionPointName.create("com.android.project.buildSystemService"),
            BuildSystemService.class)
        .registerExtension(new BlazeBuildSystemService());
  }

  /**
   * If we have a resource module and a target directory, then we can get the res dir from the
   * module, and use the target directory for everything else.
   */
  @Test
  public void getResourceSourceSetsWithTargetDirectory() {
    AndroidFacet facet = mockResourceFacet();
    File resourceFile = VfsUtilCore.virtualToIoFile(resource);
    File targetFile = VfsUtilCore.virtualToIoFile(target);
    List<AndroidSourceSet> sourceSets = AndroidSourceSet.getSourceSets(facet, target);
    assertThat(sourceSets).hasSize(1);
    AndroidSourceSet sourceSet = sourceSets.get(0);
    AndroidProjectPaths paths = sourceSet.getPaths();
    assertThat(sourceSet.getName()).isEqualTo("com.google.target");
    assertThat(paths.getModuleRoot()).isEqualTo(resourceFile);
    assertThat(paths.getSrcDirectory(null)).isEqualTo(targetFile);
    assertThat(paths.getTestDirectory(null)).isEqualTo(targetFile);
    assertThat(paths.getResDirectory()).isEqualTo(new File(resourceFile, "res"));
    assertThat(paths.getAidlDirectory(null)).isEqualTo(targetFile);
    assertThat(paths.getManifestDirectory()).isEqualTo(targetFile);
  }

  /**
   * If we have a target directory but no resource module, we'll assume the res dir is just
   * target/res.
   */
  @Test
  public void getWorkspaceSourceSetsWithTargetDirectory() {
    AndroidFacet facet = mockWorkspaceFacet();
    File rootFile = VfsUtilCore.virtualToIoFile(root);
    File targetFile = VfsUtilCore.virtualToIoFile(target);
    List<AndroidSourceSet> sourceSets = AndroidSourceSet.getSourceSets(facet, target);
    assertThat(sourceSets).hasSize(1);
    AndroidSourceSet sourceSet = sourceSets.get(0);
    AndroidProjectPaths paths = sourceSet.getPaths();
    assertThat(sourceSet.getName()).isEqualTo("com.google.target");
    assertThat(paths.getModuleRoot()).isEqualTo(rootFile);
    assertThat(paths.getSrcDirectory(null)).isEqualTo(targetFile);
    assertThat(paths.getTestDirectory(null)).isEqualTo(targetFile);
    assertThat(paths.getResDirectory()).isEqualTo(new File(targetFile, "res"));
    assertThat(paths.getAidlDirectory(null)).isEqualTo(targetFile);
    assertThat(paths.getManifestDirectory()).isEqualTo(targetFile);
  }

  /**
   * If no target directory is given, but we have a resource module, we can still figure out some
   * paths.
   */
  @Test
  public void getResourceSourceSetsWithNoTargetDirectory() {
    AndroidFacet facet = mockResourceFacet();
    File rootFile = VfsUtilCore.virtualToIoFile(root);
    File resourceFile = VfsUtilCore.virtualToIoFile(resource);
    List<AndroidSourceSet> sourceSets = AndroidSourceSet.getSourceSets(facet, null);
    assertThat(sourceSets).hasSize(1);
    AndroidSourceSet sourceSet = sourceSets.get(0);
    AndroidProjectPaths paths = sourceSet.getPaths();
    assertThat(sourceSet.getName()).isEqualTo("com.google.resource");
    assertThat(paths.getModuleRoot()).isEqualTo(resourceFile);
    assertThat(paths.getSrcDirectory(null)).isEqualTo(resourceFile);
    assertThat(paths.getTestDirectory(null)).isEqualTo(resourceFile);
    assertThat(paths.getResDirectory()).isEqualTo(new File(resourceFile, "res"));
    assertThat(paths.getAidlDirectory(null)).isEqualTo(resourceFile);
    assertThat(paths.getManifestDirectory()).isEqualTo(resourceFile);
  }

  /**
   * If no target directory is given, and we have the workspace module, we'll just use the module
   * root.
   */
  @Test
  public void getWorkspaceSourceSetsWithNoTargetDirectory() {
    AndroidFacet facet = mockWorkspaceFacet();
    File rootFile = VfsUtilCore.virtualToIoFile(root);
    List<AndroidSourceSet> sourceSets = AndroidSourceSet.getSourceSets(facet, null);
    assertThat(sourceSets).hasSize(1);
    AndroidSourceSet sourceSet = sourceSets.get(0);
    AndroidProjectPaths paths = sourceSet.getPaths();
    assertThat(sourceSet.getName()).isEqualTo(".workspace");
    assertThat(paths.getModuleRoot()).isEqualTo(rootFile);
    assertThat(paths.getSrcDirectory(null)).isEqualTo(rootFile);
    assertThat(paths.getTestDirectory(null)).isEqualTo(rootFile);
    assertThat(paths.getResDirectory()).isEqualTo(new File(rootFile, "res"));
    assertThat(paths.getAidlDirectory(null)).isEqualTo(rootFile);
    assertThat(paths.getManifestDirectory()).isEqualTo(rootFile);
  }

  private void mockBlazeImportSettings(Container projectServices) {
    BlazeImportSettingsManager importSettingsManager = new BlazeImportSettingsManager();
    importSettingsManager.setImportSettings(
        new BlazeImportSettings("", "", "", "", Blaze.BuildSystem.Blaze));
    projectServices.register(BlazeImportSettingsManager.class, importSettingsManager);
  }

  private void mockPsiPackage(Container applicationServices, Container projectServices) {
    projectServices.register(PsiManager.class, mock(PsiManager.class));
    applicationServices.register(JavaDirectoryService.class, mock(JavaDirectoryService.class));
    PsiManager manager = PsiManager.getInstance(project);
    PsiDirectory targetPsiDirectory = mock(PsiDirectory.class);
    PsiPackage targetPsiPackage = new PsiPackageImpl(manager, "com.google.target");
    when(PsiManager.getInstance(project).findDirectory(target)).thenReturn(targetPsiDirectory);
    when(JavaDirectoryService.getInstance().getPackage(targetPsiDirectory))
        .thenReturn(targetPsiPackage);
  }

  private void mockFacetRegistry(Container applicationServices) {
    applicationServices.register(FacetTypeRegistry.class, new FacetTypeRegistryImpl());
    registerExtensionPoint(FacetType.EP_NAME, FacetType.class)
        .registerExtension(new AndroidFacetType());
  }

  private AndroidFacet mockWorkspaceFacet() {
    String name = ".workspace";
    File rootFile = VfsUtilCore.virtualToIoFile(root);
    SourceProvider sourceProvider =
        new SourceProviderImpl(name, new File(rootFile, "AndroidManifest.xml"), ImmutableList.of());
    return new MockAndroidFacet(project, name, root, sourceProvider);
  }

  private AndroidFacet mockResourceFacet() {
    String name = "com.google.resource";
    File resourceFile = VfsUtilCore.virtualToIoFile(resource);
    SourceProvider sourceProvider =
        new SourceProviderImpl(
            name,
            new File(resourceFile, "AndroidManifest.xml"),
            ImmutableList.of(new File(resourceFile, "res")));
    return new MockAndroidFacet(project, name, resource, sourceProvider);
  }

  private static class MockAndroidFacet extends AndroidFacet {
    private SourceProvider sourceProvider;

    public MockAndroidFacet(
        Project project, String name, VirtualFile root, SourceProvider sourceProvider) {
      super(new MockModule(project, () -> {}), AndroidFacet.NAME, new AndroidFacetConfiguration());
      MockModule module = (MockModule) getModule();
      module.setName(name);
      ModuleRootManager rootManager = mock(ModuleRootManager.class);
      when(rootManager.getContentRoots()).thenReturn(new VirtualFile[] {root});
      module.addComponent(ModuleRootManager.class, rootManager);
      this.sourceProvider = sourceProvider;
    }

    @Override
    public SourceProvider getMainSourceProvider() {
      return sourceProvider;
    }
  }
}
