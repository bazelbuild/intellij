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
package com.google.idea.blaze.golang.run;

import static com.google.common.truth.Truth.assertThat;

import com.goide.dlv.location.DlvPositionConverter;
import com.goide.dlv.location.DlvPositionConverterFactory;
import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.base.BlazeTestCase;
import com.google.idea.blaze.base.bazel.BazelBuildSystemProvider;
import com.google.idea.blaze.base.bazel.BuildSystemProvider;
import com.google.idea.blaze.base.io.VirtualFileSystemProvider;
import com.google.idea.blaze.base.model.MockBlazeProjectDataBuilder;
import com.google.idea.blaze.base.model.MockBlazeProjectDataManager;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.settings.BlazeImportSettings;
import com.google.idea.blaze.base.settings.BlazeImportSettingsManager;
import com.google.idea.blaze.base.settings.BuildSystem;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.base.sync.workspace.ExecutionRootPathResolver;
import com.google.idea.blaze.base.sync.workspace.WorkspacePathResolverImpl;
import com.intellij.mock.MockLocalFileSystem;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import java.io.File;
import java.util.Set;
import javax.annotation.Nullable;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link BlazeDlvPositionConverter} */
@RunWith(JUnit4.class)
public class BlazeDlvPositionConverterTest extends BlazeTestCase {
  private PartialMockLocalFileSystem fileSystem;
  private File executionRoot;
  private WorkspaceRoot workspaceRoot;

  @Override
  protected void initTest(Container applicationServices, Container projectServices) {
    super.initTest(applicationServices, projectServices);
    fileSystem = new PartialMockLocalFileSystem();
    applicationServices.register(VirtualFileSystemProvider.class, () -> fileSystem);
    BlazeImportSettingsManager importSettingsManager = new BlazeImportSettingsManager();
    BlazeImportSettings importSettings =
        new BlazeImportSettings("/root", "", "", "", BuildSystem.Bazel);
    workspaceRoot = WorkspaceRoot.fromImportSettings(importSettings);
    importSettingsManager.setImportSettings(importSettings);
    projectServices.register(BlazeImportSettingsManager.class, importSettingsManager);
    projectServices.register(
        BlazeProjectDataManager.class,
        new MockBlazeProjectDataManager(
            MockBlazeProjectDataBuilder.builder()
                .setWorkspacePathResolver(new WorkspacePathResolverImpl(workspaceRoot))
                .build()));
    registerExtensionPoint(BuildSystemProvider.EP_NAME, BuildSystemProvider.class)
        .registerExtension(new BazelBuildSystemProvider());
    registerExtensionPoint(DlvPositionConverterFactory.EP_NAME, DlvPositionConverterFactory.class)
        .registerExtension(new BlazeDlvPositionConverter.Factory());
    ExecutionRootPathResolver resolver = ExecutionRootPathResolver.fromProject(getProject());
    assertThat(resolver).isNotNull();
    executionRoot = resolver.getExecutionRoot();
  }

  @Test
  public void testConverterCreated() {
    DlvPositionConverter converter =
        DlvPositionConverterFactory.create(getProject(), null, ImmutableSet.of());
    assertThat(converter).isInstanceOf(BlazeDlvPositionConverter.class);
  }

  @Test
  public void testConvertWorkspacePaths() {
    fileSystem.setResolvablePaths(workspacePath("foo/bar.go"), workspacePath("one/two.go"));
    DlvPositionConverter converter =
        DlvPositionConverterFactory.create(
            getProject(), null, ImmutableSet.of("foo/bar.go", "one/two.go"));
    assertThatFile(converter.toLocalFile("foo/bar.go")).hasWorkspacePath("foo/bar.go");
    assertThatFile(converter.toLocalFile("one/two.go")).hasWorkspacePath("one/two.go");
    assertThat(converter.toRemotePath(workspaceFile("foo/bar.go"))).isEqualTo("foo/bar.go");
    assertThat(converter.toRemotePath(workspaceFile("one/two.go"))).isEqualTo("one/two.go");
  }

  @Test
  public void testConvertAbsolutePaths() {
    fileSystem.setResolvablePaths("/absolute/path.go");
    DlvPositionConverter converter =
        DlvPositionConverterFactory.create(
            getProject(), null, ImmutableSet.of("/absolute/path.go"));
    assertThatFile(converter.toLocalFile("/absolute/path.go")).hasPath("/absolute/path.go");
    assertThat(converter.toRemotePath(fileForPath("/absolute/path.go")))
        .isEqualTo("/absolute/path.go");
  }

  @Test
  public void testConvertGenfilePaths() {
    fileSystem.setResolvablePaths(
        executionRootPath("bazel-genfiles/foo/bar.go"),
        executionRootPath("bazel-genfiles/one/two.go"));
    DlvPositionConverter converter =
        DlvPositionConverterFactory.create(
            getProject(),
            null,
            ImmutableSet.of("bazel-genfiles/foo/bar.go", "bazel-genfiles/one/two.go"));
    assertThatFile(converter.toLocalFile("bazel-genfiles/foo/bar.go"))
        .hasExecutionRootPath("bazel-genfiles/foo/bar.go");
    assertThatFile(converter.toLocalFile("bazel-genfiles/one/two.go"))
        .hasExecutionRootPath("bazel-genfiles/one/two.go");
    assertThat(converter.toRemotePath(executionRootFile("bazel-genfiles/foo/bar.go")))
        .isEqualTo("bazel-genfiles/foo/bar.go");
    assertThat(converter.toRemotePath(executionRootFile("bazel-genfiles/one/two.go")))
        .isEqualTo("bazel-genfiles/one/two.go");
  }

  @Test
  public void testConvertNewFileToRemote() {
    fileSystem.setResolvablePaths(
        workspacePath("foo/bar.go"), workspacePath("one/two.go"), workspacePath("three/four.go"));
    DlvPositionConverter converter =
        DlvPositionConverterFactory.create(
            getProject(), null, ImmutableSet.of("foo/bar.go", "one/two.go"));
    assertThat(converter.toRemotePath(workspaceFile("three/four.go"))).isEqualTo("three/four.go");
  }

  @Test
  public void testConvertNewPathToLocal() {
    fileSystem.setResolvablePaths(
        workspacePath("foo/bar.go"), workspacePath("one/two.go"), workspacePath("three/four.go"));
    DlvPositionConverter converter =
        DlvPositionConverterFactory.create(
            getProject(), null, ImmutableSet.of("foo/bar.go", "one/two.go"));
    assertThatFile(converter.toLocalFile("three/four.go")).hasWorkspacePath("three/four.go");
  }

  @Test
  public void testConvertNoneExistentPathToLocal() {
    fileSystem.setResolvablePaths(workspacePath("foo/bar.go"), workspacePath("one/two.go"));
    DlvPositionConverter converter =
        DlvPositionConverterFactory.create(
            getProject(), null, ImmutableSet.of("foo/bar.go", "one/two.go"));
    assertThat(converter.toLocalFile("not/exist.go")).isNull();
  }

  @Test
  public void testConvertNoneWorkspaceFileToRemote() {
    fileSystem.setResolvablePaths(
        workspacePath("foo/bar.go"), workspacePath("one/two.go"), "/outside/root.go");
    DlvPositionConverter converter =
        DlvPositionConverterFactory.create(
            getProject(), null, ImmutableSet.of("foo/bar.go", "one/two.go"));
    assertThat(converter.toRemotePath(fileForPath("/outside/root.go")))
        .isEqualTo("/outside/root.go");
  }

  @Test
  public void testConvertNormalizedPaths() {
    fileSystem.setResolvablePaths(workspacePath("foo/bar.go"), workspacePath("one/two.go"));
    DlvPositionConverter converter =
        DlvPositionConverterFactory.create(
            getProject(),
            null,
            ImmutableSet.of(
                "foo/bar.go",
                "one/two.go",
                "/build/work/1234/project/foo/bar.go",
                "/tmp/go-build-release/buildroot/one/two.go"));
    assertThatFile(converter.toLocalFile("foo/bar.go")).hasWorkspacePath("foo/bar.go");
    assertThatFile(converter.toLocalFile("one/two.go")).hasWorkspacePath("one/two.go");
    assertThatFile(converter.toLocalFile("/build/work/1234/project/foo/bar.go"))
        .hasWorkspacePath("foo/bar.go");
    assertThatFile(converter.toLocalFile("/tmp/go-build-release/buildroot/one/two.go"))
        .hasWorkspacePath("one/two.go");
    assertThat(converter.toRemotePath(workspaceFile("foo/bar.go"))).isEqualTo("foo/bar.go");
    assertThat(converter.toRemotePath(workspaceFile("one/two.go"))).isEqualTo("one/two.go");
  }

  @Test
  public void testConvertNormalizedNewPaths() {
    fileSystem.setResolvablePaths(workspacePath("foo/bar.go"), workspacePath("one/two.go"));
    DlvPositionConverter converter =
        DlvPositionConverterFactory.create(getProject(), null, ImmutableSet.of());
    assertThatFile(converter.toLocalFile("/build/work/1234/project/foo/bar.go"))
        .hasWorkspacePath("foo/bar.go");
    assertThatFile(converter.toLocalFile("/tmp/go-build-release/buildroot/one/two.go"))
        .hasWorkspacePath("one/two.go");
  }

  @Test
  public void testFailedToNormalize() {
    fileSystem.setResolvablePaths(
        workspacePath("foo.go"),
        workspacePath("one/two.go"),
        workspacePath("bar.go"),
        "/build/work/foo.go",
        "/build/work/one/two.go",
        "/tmp/go-build-release/bar.go");
    DlvPositionConverter converter =
        DlvPositionConverterFactory.create(
            getProject(),
            null,
            ImmutableSet.of(
                "../../bogus.go",
                "/build/work/foo.go",
                "/build/work/one/two.go",
                "/tmp/go-build-release/bar.go"));
    assertThat(converter.toLocalFile("../../bogus.go")).isNull();
    assertThatFile(converter.toLocalFile("/build/work/foo.go")).hasPath("/build/work/foo.go");
    assertThatFile(converter.toLocalFile("/build/work/one/two.go"))
        .hasPath("/build/work/one/two.go");
    assertThatFile(converter.toLocalFile("/tmp/go-build-release/bar.go"))
        .hasPath("/tmp/go-build-release/bar.go");
  }

  private VirtualFile fileForPath(String path) {
    VirtualFile file = fileSystem.findFileByPath(path);
    assertThat(file).isNotNull();
    return file;
  }

  private VirtualFile workspaceFile(String relativePath) {
    return fileForPath(workspacePath(relativePath));
  }

  private VirtualFile executionRootFile(String relativePath) {
    return fileForPath(executionRootPath(relativePath));
  }

  private String workspacePath(String relativePath) {
    return new File(workspaceRoot.directory(), relativePath).getPath();
  }

  private String executionRootPath(String relativePath) {
    return new File(executionRoot, relativePath).getPath();
  }

  interface FileSubject {
    void hasPath(String path);

    void hasWorkspacePath(String path);

    void hasExecutionRootPath(String path);
  }

  private FileSubject assertThatFile(VirtualFile localFile) {
    return new FileSubject() {
      @Override
      public void hasPath(String path) {
        assertThat(localFile).isNotNull();
        assertThat(localFile).isEqualTo(fileSystem.findFileByPath(path));
      }

      @Override
      public void hasWorkspacePath(String relativePath) {
        hasPath(workspacePath(relativePath));
      }

      @Override
      public void hasExecutionRootPath(String relativePath) {
        hasPath(executionRootPath(relativePath));
      }
    };
  }

  /** {@link MockLocalFileSystem} that can resolve a predetermined list of paths, but not others. */
  private static class PartialMockLocalFileSystem extends MockLocalFileSystem {
    private Set<String> resolvablePaths;

    void setResolvablePaths(String... resolvablePaths) {
      this.resolvablePaths = ImmutableSet.copyOf(resolvablePaths);
    }

    @Nullable
    @Override
    public VirtualFile findFileByPath(String path) {
      return resolvablePaths.contains(path) ? super.findFileByPath(path) : null;
    }

    @Nullable
    @Override
    public VirtualFile findFileByIoFile(File file) {
      return this.findFileByPath(FileUtil.toSystemIndependentName(file.getPath()));
    }
  }
}
