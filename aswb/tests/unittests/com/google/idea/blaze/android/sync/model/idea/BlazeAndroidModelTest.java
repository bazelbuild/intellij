/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.android.sync.model.idea;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.android.builder.model.SourceProvider;
import com.android.tools.idea.model.AndroidModel;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.idea.blaze.base.BlazeTestCase;
import com.google.idea.blaze.base.actions.BlazeBuildService;
import com.google.idea.blaze.base.projectview.ProjectViewManager;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.settings.Blaze.BuildSystem;
import com.google.idea.blaze.base.settings.BlazeImportSettings;
import com.google.idea.blaze.base.settings.BlazeImportSettingsManager;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManagerImpl;
import com.google.idea.blaze.base.sync.workspace.WorkspacePathResolver;
import com.intellij.mock.MockFileDocumentManagerImpl;
import com.intellij.mock.MockModule;
import com.intellij.mock.MockPsiManager;
import com.intellij.mock.MockVirtualFile;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.MockFileTypeManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.VirtualFileSystem;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.JavaPsiFacadeImpl;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.ProjectScopeBuilder;
import com.intellij.psi.search.ProjectScopeBuilderImpl;
import javax.annotation.Nullable;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Test cases for {@link BlazeAndroidModel}. */
@RunWith(JUnit4.class)
public class BlazeAndroidModelTest extends BlazeTestCase {
  private Module module;
  private AndroidModel model;
  private MockJavaPsiFacade facade;

  @Override
  protected void initTest(Container applicationServices, Container projectServices) {
    applicationServices.register(FileTypeManager.class, new MockFileTypeManager());
    applicationServices.register(
        FileDocumentManager.class, new MockFileDocumentManagerImpl(null, null));
    applicationServices.register(VirtualFileManager.class, mock(VirtualFileManager.class));
    applicationServices.register(BlazeBuildService.class, new BlazeBuildService());
    projectServices.register(ProjectScopeBuilder.class, new ProjectScopeBuilderImpl(project));
    projectServices.register(ProjectViewManager.class, new MockProjectViewManager());
    projectServices.register(
        BlazeProjectDataManager.class, new BlazeProjectDataManagerImpl(project));

    BlazeImportSettingsManager manager = new BlazeImportSettingsManager();
    manager.setImportSettings(new BlazeImportSettings("", "", "", "", BuildSystem.Blaze));
    projectServices.register(BlazeImportSettingsManager.class, manager);

    facade =
        new MockJavaPsiFacade(
            project,
            new MockPsiManager(project),
            ImmutableList.of("com.google.example.Modified", "com.google.example.NotModified"));

    projectServices.register(JavaPsiFacade.class, facade);
    module = new MockModule(() -> {});
    model = new BlazeAndroidModel(project, module, null, mock(SourceProvider.class), null, "", 0);
  }

  @Test
  public void testIsClassFileOutOfDate() {
    VirtualFile modifiedJarFile =
        new MockJarVirtualFile(
            "/build/com/google/example/libmodified.jar",
            facade.getTimestamp("com.google.example.Modified") - 100);
    VirtualFile notModifiedJarFile =
        new MockJarVirtualFile(
            "/build/com/google/example/libnotmodified.jar",
            facade.getTimestamp("com.google.example.NotModified") + 100);
    VirtualFile modifiedClassFile =
        new MockClassVirtualFile(
            "/build/com/google/example/libmodified.jar!/com/google/example/Modified.class",
            modifiedJarFile);
    VirtualFile notModifiedClassFile =
        new MockClassVirtualFile(
            "/build/com/google/example/libnotmodified.jar!/com/google/example/NotModified.class",
            notModifiedJarFile);
    assertThat(model.isClassFileOutOfDate(module, "com.google.example.Modified", modifiedClassFile))
        .isTrue();
    assertThat(
            model.isClassFileOutOfDate(
                module, "com.google.example.NotModified", notModifiedClassFile))
        .isFalse();

    BlazeBuildService.getInstance().buildProject(project);
    assertThat(model.isClassFileOutOfDate(module, "com.google.example.Modified", modifiedClassFile))
        .isFalse();
    assertThat(
            model.isClassFileOutOfDate(
                module, "com.google.example.NotModified", notModifiedClassFile))
        .isFalse();
  }

  private static class MockJavaPsiFacade extends JavaPsiFacadeImpl {
    private ImmutableMap<String, PsiClass> classes;
    private ImmutableMap<String, Long> timestamps;

    MockJavaPsiFacade(
        Project project, PsiManager psiManager, ImmutableCollection<String> classNames) {
      super(project, psiManager, null, null);
      ImmutableMap.Builder<String, PsiClass> classesBuilder = ImmutableMap.builder();
      ImmutableMap.Builder<String, Long> timestampsBuilder = ImmutableMap.builder();
      for (String className : classNames) {
        VirtualFile virtualFile =
            new MockVirtualFile("/src/" + className.replace('.', '/') + ".java");
        PsiFile psiFile = mock(PsiFile.class);
        when(psiFile.getVirtualFile()).thenReturn(virtualFile);
        PsiClass psiClass = mock(PsiClass.class);
        when(psiClass.getContainingFile()).thenReturn(psiFile);
        classesBuilder.put(className, psiClass);
        timestampsBuilder.put(className, virtualFile.getTimeStamp());
      }
      classes = classesBuilder.build();
      timestamps = timestampsBuilder.build();
    }

    @Nullable
    @Override
    public PsiClass findClass(String qualifiedName, GlobalSearchScope scope) {
      if (scope.equals(GlobalSearchScope.projectScope(getProject()))) {
        return classes.get(qualifiedName);
      }
      return null;
    }

    long getTimestamp(String qualifiedName) {
      return timestamps.get(qualifiedName);
    }
  }

  private static class MockClassVirtualFile extends MockVirtualFile {
    private static JarFileSystem fileSystem = mock(JarFileSystem.class);

    MockClassVirtualFile(String name, VirtualFile jar) {
      super(name);
      when(fileSystem.getVirtualFileForJar(this)).thenReturn(jar);
    }

    @Override
    public VirtualFileSystem getFileSystem() {
      return fileSystem;
    }
  }

  private static class MockJarVirtualFile extends MockVirtualFile {
    private long timestamp;

    MockJarVirtualFile(String name, long timestamp) {
      super(name);
      this.timestamp = timestamp;
    }

    @Override
    public long getTimeStamp() {
      return timestamp;
    }
  }

  private static class MockProjectViewManager extends ProjectViewManager {
    private ProjectViewSet set = new ProjectViewSet(ImmutableList.of());

    @Nullable
    @Override
    public ProjectViewSet getProjectViewSet() {
      return set;
    }

    @Nullable
    @Override
    public ProjectViewSet reloadProjectView(BlazeContext context) {
      return null;
    }

    @Nullable
    @Override
    public ProjectViewSet reloadProjectView(
        BlazeContext context, WorkspacePathResolver workspacePathResolver) {
      return null;
    }
  }
}
