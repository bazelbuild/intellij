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
package com.google.idea.blaze.base.sync.libraries;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.idea.blaze.base.BlazeTestCase;
import com.google.idea.blaze.base.io.VirtualFileSystemProvider;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.MockBlazeProjectDataBuilder;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.sync.SyncListener;
import com.google.idea.blaze.base.sync.SyncMode;
import com.google.idea.blaze.base.sync.SyncResult;
import com.google.idea.blaze.base.sync.libraries.ExternalLibraryManager.SyncPlugin;
import com.google.idea.sdkcompat.openapi.VFileCreateEventCompat;
import com.intellij.mock.MockLocalFileSystem;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.AdditionalLibraryRootsProvider;
import com.intellij.openapi.roots.SyntheticLibrary;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.vfs.AsyncVfsEventsListener;
import com.intellij.vfs.AsyncVfsEventsPostProcessor;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Test cases for {@link ExternalLibraryManager}. */
@RunWith(JUnit4.class)
public final class ExternalLibraryManagerTest extends BlazeTestCase {
  private MockFileSystem fileSystem;
  private MockExternalLibraryProvider libraryProvider;
  private SyncListener syncListener;
  private SyncPlugin syncPlugin;
  private List<AsyncVfsEventsListener> vfsListeners;

  @Override
  protected void initTest(Container applicationServices, Container projectServices) {
    super.initTest(applicationServices, projectServices);
    fileSystem = new MockFileSystem();
    libraryProvider = new MockExternalLibraryProvider();
    syncListener = new ExternalLibraryManager.StartSyncListener();
    syncPlugin = new ExternalLibraryManager.SyncPlugin();
    vfsListeners = new ArrayList<>();
    applicationServices.register(
        AsyncVfsEventsPostProcessor.class, (listener, disposable) -> vfsListeners.add(listener));
    applicationServices.register(VirtualFileSystemProvider.class, () -> fileSystem);
    projectServices.register(ExternalLibraryManager.class, new ExternalLibraryManager(project));
    registerExtensionPoint(
            AdditionalLibraryRootsProvider.EP_NAME, AdditionalLibraryRootsProvider.class)
        .registerExtension(libraryProvider);
  }

  @Test
  public void testFilesFound() {
    VirtualFile fooFile = fileSystem.createFile("/src/foo/Foo.java");
    assertThat(fooFile).isNotNull();
    VirtualFile barFile = fileSystem.createFile("/src/bar/Bar.java");
    assertThat(barFile).isNotNull();

    libraryProvider.setFiles("/src/foo/Foo.java", "/src/bar/Bar.java");
    mockSync(SyncResult.SUCCESS);

    Collection<VirtualFile> libraryRoots = getExternalLibrary().getSourceRoots();
    assertThat(libraryRoots).containsExactly(fooFile, barFile);
  }

  @Test
  public void testFileRemoved() {
    VirtualFile fooFile = fileSystem.createFile("/src/foo/Foo.java");
    assertThat(fooFile).isNotNull();
    VirtualFile barFile = fileSystem.createFile("/src/bar/Bar.java");
    assertThat(barFile).isNotNull();

    libraryProvider.setFiles("/src/foo/Foo.java", "/src/bar/Bar.java");
    mockSync(SyncResult.SUCCESS);

    Collection<VirtualFile> libraryRoots = getExternalLibrary().getSourceRoots();
    assertThat(libraryRoots).containsExactly(fooFile, barFile);

    fileSystem.removeFile("/src/bar/Bar.java");
    assertThat(libraryRoots).containsExactly(fooFile);

    fileSystem.removeFile("/src/foo/Foo.java");
    assertThat(libraryRoots).isEmpty();
  }

  @Test
  public void testSuccessfulSync() {
    // both old and new files exist, project data is changed
    VirtualFile oldFile = fileSystem.createFile("/src/old/Old.java");
    assertThat(oldFile).isNotNull();
    VirtualFile newFile = fileSystem.createFile("/src/new/New.java");
    assertThat(newFile).isNotNull();

    libraryProvider.setFiles("/src/old/Old.java");
    mockSync(SyncResult.SUCCESS);
    assertThat(getExternalLibrary().getSourceRoots()).containsExactly(oldFile);

    libraryProvider.setFiles("/src/new/New.java");
    mockSync(SyncResult.SUCCESS);
    assertThat(getExternalLibrary().getSourceRoots()).containsExactly(newFile);
  }

  @Test
  public void testFailedSync() {
    // both old and new files exist, project data is changed
    VirtualFile oldFile = fileSystem.createFile("/src/old/Old.java");
    assertThat(oldFile).isNotNull();
    VirtualFile newFile = fileSystem.createFile("/src/new/New.java");
    assertThat(newFile).isNotNull();

    libraryProvider.setFiles("/src/old/Old.java");
    mockSync(SyncResult.SUCCESS);
    assertThat(getExternalLibrary().getSourceRoots()).containsExactly(oldFile);

    libraryProvider.setFiles("/src/new/New.java");
    mockSync(SyncResult.FAILURE);
    // files list should remain the same if sync failed
    assertThat(getExternalLibrary().getSourceRoots()).containsExactly(oldFile);
  }

  @Test
  public void testDuringSuccessfulSync() {
    VirtualFile oldFile = fileSystem.createFile("/src/foo/Foo.java");
    assertThat(oldFile).isNotNull();

    libraryProvider.setFiles("/src/foo/Foo.java");
    mockSync(SyncResult.SUCCESS);
    assertThat(libraryProvider.getAdditionalProjectLibraries(project)).isNotEmpty();

    BlazeContext context = new BlazeContext();
    syncListener.onSyncStart(project, context, SyncMode.INCREMENTAL);
    assertThat(libraryProvider.getAdditionalProjectLibraries(project)).isEmpty();

    syncPlugin.updateProjectStructure(
        project,
        context,
        null,
        new ProjectViewSet(ImmutableList.of()),
        MockBlazeProjectDataBuilder.builder().build(),
        null,
        null,
        null,
        null);
    assertThat(libraryProvider.getAdditionalProjectLibraries(project)).isNotEmpty();

    syncListener.afterSync(project, context, SyncMode.INCREMENTAL, SyncResult.SUCCESS);
    assertThat(libraryProvider.getAdditionalProjectLibraries(project)).isNotEmpty();
  }

  @Test
  public void testDuringFailedSync() {
    VirtualFile oldFile = fileSystem.createFile("/src/foo/Foo.java");
    assertThat(oldFile).isNotNull();

    libraryProvider.setFiles("/src/foo/Foo.java");
    mockSync(SyncResult.SUCCESS);
    assertThat(libraryProvider.getAdditionalProjectLibraries(project)).isNotEmpty();

    BlazeContext context = new BlazeContext();
    syncListener.onSyncStart(project, context, SyncMode.INCREMENTAL);
    assertThat(libraryProvider.getAdditionalProjectLibraries(project)).isEmpty();

    syncListener.afterSync(project, context, SyncMode.INCREMENTAL, SyncResult.FAILURE);
    assertThat(libraryProvider.getAdditionalProjectLibraries(project)).isNotEmpty();
  }

  private void mockSync(SyncResult syncResult) {
    BlazeContext context = new BlazeContext();
    syncListener.onSyncStart(project, context, SyncMode.INCREMENTAL);
    if (syncResult.successful()) {
      syncPlugin.updateProjectStructure(
          project,
          context,
          null,
          new ProjectViewSet(ImmutableList.of()),
          MockBlazeProjectDataBuilder.builder().build(),
          null,
          null,
          null,
          null);
    }
    syncListener.afterSync(project, context, SyncMode.INCREMENTAL, syncResult);
  }

  private SyntheticLibrary getExternalLibrary() {
    Collection<SyntheticLibrary> libraries = libraryProvider.getAdditionalProjectLibraries(project);
    assertThat(libraries).hasSize(1);
    SyntheticLibrary library = Iterables.getFirst(libraries, null);
    assertThat(library).isNotNull();
    return library;
  }

  private final class MockFileSystem extends MockLocalFileSystem {
    private final Set<String> paths = new HashSet<>();

    VirtualFile createFile(String path) {
      VirtualFile file = super.findFileByPath(path);
      assertThat(file).isNotNull();
      paths.add(path);
      List<VFileEvent> events =
          ImmutableList.of(
              new VFileCreateEventCompat(this, file.getParent(), file.getName(), false, false));
      vfsListeners.forEach(listener -> listener.filesChanged(events));
      return file;
    }

    void removeFile(String path) {
      VirtualFile file = super.findFileByPath(path);
      assertThat(file).isNotNull();
      paths.remove(path);
      List<VFileEvent> events = ImmutableList.of(new VFileDeleteEvent(this, file, false));
      vfsListeners.forEach(listener -> listener.filesChanged(events));
    }

    @Nullable
    @Override
    public VirtualFile findFileByIoFile(File file) {
      return findFileByPath(file.getPath());
    }

    @Nullable
    @Override
    public VirtualFile findFileByPath(String path) {
      return paths.contains(path) ? super.findFileByPath(path) : null;
    }
  }

  private static final class MockExternalLibraryProvider extends BlazeExternalLibraryProvider {
    private ImmutableList<File> files = ImmutableList.of();

    @Override
    protected String getLibraryName() {
      return "Mock Libraries";
    }

    @Override
    protected ImmutableList<File> getLibraryFiles(Project project, BlazeProjectData projectData) {
      return files;
    }

    void setFiles(String... paths) {
      this.files = Arrays.stream(paths).map(File::new).collect(toImmutableList());
    }
  }
}
