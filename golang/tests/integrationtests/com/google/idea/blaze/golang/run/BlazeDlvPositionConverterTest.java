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
import com.goide.sdk.GoSdkImpl;
import com.goide.sdk.GoSdkService;
import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.base.BlazeIntegrationTestCase;
import com.google.idea.blaze.base.bazel.BazelBuildSystemProvider;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.ideinfo.GoIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetMapBuilder;
import com.google.idea.blaze.base.io.VirtualFileSystemProvider;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.MockBlazeProjectDataBuilder;
import com.google.idea.blaze.base.model.MockBlazeProjectDataManager;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.base.sync.workspace.ExecutionRootPathResolver;
import com.google.idea.blaze.base.sync.workspace.WorkspacePathResolverImpl;
import com.intellij.mock.MockLocalFileSystem;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import java.io.File;
import java.util.Set;
import javax.annotation.Nullable;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link BlazeDlvPositionConverter} */
@RunWith(JUnit4.class)
public class BlazeDlvPositionConverterTest extends BlazeIntegrationTestCase {
  private File executionRoot;
  private PartialMockLocalFileSystem mockFileSystem;
  private BlazeDlvPositionConverter.CgoTrimmedPathsHandler cgoTrimmedPathsHandler;

  @Before
  public void init() {
    GoSdkService.getInstance(getProject()).setSdk(new GoSdkImpl("/usr/lib/golang", null, null));
    registerApplicationService(VirtualFileSystemProvider.class, () -> mockFileSystem);
    String pathThatConflictsWithBazelWorkspace = "_main/";
    TargetMapBuilder cgoTestTargetMapBuilder = TargetMapBuilder.builder()
            .addTarget(
                    TargetIdeInfo.builder()
                            .setLabel("//cgo1:test")
                            .setGoInfo(GoIdeInfo.builder().setIsCgo(true)
                                    .addSource(ArtifactLocation.builder().setRelativePath("cgo.go").setIsSource(true).build())
                                    .addSource(ArtifactLocation.builder().setRelativePath("collision1.go").setIsSource(true).build())
                                    .addSource(ArtifactLocation.builder().setRelativePath("collision2.go").setIsSource(true).build())
                            ).build())
            .addTarget(
                    TargetIdeInfo.builder()
                            .setLabel("//noCgo:test")
                            .setGoInfo(GoIdeInfo.builder().setIsCgo(false)
                                    .addSource(ArtifactLocation.builder().setRelativePath("nonCgo.go").setIsSource(true).build())
                                    .addSource(ArtifactLocation.builder().setRelativePath(pathThatConflictsWithBazelWorkspace + "collision1.go").setIsSource(true).build())
                            ).build()
            )
            .addTarget(
                    TargetIdeInfo.builder()
                            .setLabel("//cgo2:test")
                            .setGoInfo(GoIdeInfo.builder().setIsCgo(true)
                                    .addSource(ArtifactLocation.builder().setRelativePath(pathThatConflictsWithBazelWorkspace + "collision2.go").setIsSource(true).build())
                            ).build());
    BlazeProjectData projectData =
        MockBlazeProjectDataBuilder.builder()
            .setWorkspacePathResolver(new WorkspacePathResolverImpl(workspaceRoot))
            .setTargetMap(cgoTestTargetMapBuilder.build())
            .build();
    registerProjectService(
        BlazeProjectDataManager.class, new MockBlazeProjectDataManager(projectData));
    executionRoot = projectData.getBlazeInfo().getExecutionRoot();
    mockFileSystem = new PartialMockLocalFileSystem();
    ExecutionRootPathResolver resolver = new ExecutionRootPathResolver(
                    new BazelBuildSystemProvider(),
                    workspaceRoot,
                    executionRoot,
                    projectData.getBlazeInfo().getOutputBase(),
                    new WorkspacePathResolverImpl(workspaceRoot),
                    cgoTestTargetMapBuilder.build());
    cgoTrimmedPathsHandler = new BlazeDlvPositionConverter.CgoTrimmedPathsHandler(getProject(), resolver);
  }

  @Test
  public void testConverterCreated() {
    DlvPositionConverter converter =
        DlvPositionConverterFactory.create(getProject(), null, ImmutableSet.of());
    assertThat(converter).isInstanceOf(BlazeDlvPositionConverter.class);
  }

  @Test
  public void testConvertWorkspacePaths() {
    mockFileSystem.setResolvablePaths(workspacePath("foo/bar.go"), workspacePath("one/two.go"));
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
    mockFileSystem.setResolvablePaths("/absolute/path.go");
    DlvPositionConverter converter =
        DlvPositionConverterFactory.create(
            getProject(), null, ImmutableSet.of("/absolute/path.go"));
    assertThatFile(converter.toLocalFile("/absolute/path.go")).hasPath("/absolute/path.go");
    assertThat(converter.toRemotePath(fileForPath("/absolute/path.go")))
        .isEqualTo("/absolute/path.go");
  }

  @Test
  public void testConvertGenfilePaths() {
    mockFileSystem.setResolvablePaths(
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
    mockFileSystem.setResolvablePaths(
        workspacePath("foo/bar.go"), workspacePath("one/two.go"), workspacePath("three/four.go"));
    DlvPositionConverter converter =
        DlvPositionConverterFactory.create(
            getProject(), null, ImmutableSet.of("foo/bar.go", "one/two.go"));
    assertThat(converter.toRemotePath(workspaceFile("three/four.go"))).isEqualTo("three/four.go");
  }

  @Test
  public void testConvertNewPathToLocal() {
    mockFileSystem.setResolvablePaths(
        workspacePath("foo/bar.go"), workspacePath("one/two.go"), workspacePath("three/four.go"));
    DlvPositionConverter converter =
        DlvPositionConverterFactory.create(
            getProject(), null, ImmutableSet.of("foo/bar.go", "one/two.go"));
    assertThatFile(converter.toLocalFile("three/four.go")).hasWorkspacePath("three/four.go");
  }

  @Test
  public void testConvertNoneExistentPathToLocal() {
    mockFileSystem.setResolvablePaths(workspacePath("foo/bar.go"), workspacePath("one/two.go"));
    DlvPositionConverter converter =
        DlvPositionConverterFactory.create(
            getProject(), null, ImmutableSet.of("foo/bar.go", "one/two.go"));
    assertThat(converter.toLocalFile("not/exist.go")).isNull();
  }

  @Test
  public void testConvertNoneWorkspaceFileToRemote() {
    mockFileSystem.setResolvablePaths(
        workspacePath("foo/bar.go"), workspacePath("one/two.go"), "/outside/root.go");
    DlvPositionConverter converter =
        DlvPositionConverterFactory.create(
            getProject(), null, ImmutableSet.of("foo/bar.go", "one/two.go"));
    assertThat(converter.toRemotePath(fileForPath("/outside/root.go")))
        .isEqualTo("/outside/root.go");
  }

  @Test
  public void testConvertNormalizedPaths() {
    mockFileSystem.setResolvablePaths(workspacePath("foo/bar.go"), workspacePath("one/two.go"));
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
    mockFileSystem.setResolvablePaths(workspacePath("foo/bar.go"), workspacePath("one/two.go"));
    DlvPositionConverter converter =
        DlvPositionConverterFactory.create(getProject(), null, ImmutableSet.of());
    assertThatFile(converter.toLocalFile("/build/work/1234/project/foo/bar.go"))
        .hasWorkspacePath("foo/bar.go");
    assertThatFile(converter.toLocalFile("/tmp/go-build-release/buildroot/one/two.go"))
        .hasWorkspacePath("one/two.go");
  }

  @Test
  public void testFailedToNormalize() {
    mockFileSystem.setResolvablePaths(
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

  @Test
  public void testConvertGoRootPaths() {
    mockFileSystem.setResolvablePaths(
        workspacePath("foo/bar.go"),
        "/usr/lib/golang/src/fmt/format.go",
        "/usr/lib/golang/src/time/time.go");
    DlvPositionConverter converter =
        DlvPositionConverterFactory.create(
            getProject(),
            null,
            ImmutableSet.of("foo/bar.go", "GOROOT/src/fmt/format.go", "GOROOT/src/time/time.go"));
    assertThatFile(converter.toLocalFile("foo/bar.go")).hasWorkspacePath("foo/bar.go");
    assertThatFile(converter.toLocalFile("GOROOT/src/fmt/format.go"))
        .hasPath("/usr/lib/golang/src/fmt/format.go");
    assertThatFile(converter.toLocalFile("GOROOT/src/time/time.go"))
        .hasPath("/usr/lib/golang/src/time/time.go");
    assertThat(converter.toRemotePath(workspaceFile("foo/bar.go"))).isEqualTo("foo/bar.go");
    assertThat(converter.toRemotePath(fileForPath("/usr/lib/golang/src/fmt/format.go")))
        .isEqualTo("GOROOT/src/fmt/format.go");
    assertThat(converter.toRemotePath(fileForPath("/usr/lib/golang/src/time/time.go")))
        .isEqualTo("GOROOT/src/time/time.go");
  }

  private VirtualFile fileForPath(String path) {
    VirtualFile file = mockFileSystem.findFileByPath(path);
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
        assertThat(localFile).isEqualTo(mockFileSystem.findFileByPath(path));
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

  /**
   * {@link MockLocalFileSystem} that can resolve a predetermined list of paths, but not others.
   *
   * <p>We can't use {@link BlazeIntegrationTestCase#fileSystem} because it doesn't allow absolute
   * paths, and we need to test how the converter handles certain absolute paths.
   */
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

  @Test
  public void testConvertCgoTrimmedPaths() {
    ImmutableSet<String> remotePaths = ImmutableSet.of("cgo.go", "noCgo.go", "_main/nonExistent.go", "_main/collision1.go", "collision2.go", "_main/collision2.go", "foobar/cgo.go");
    mockFileSystem.setResolvablePaths(remotePaths.stream().map(this::workspacePath).toArray(String[]::new));

    DlvPositionConverter sut = DlvPositionConverterFactory.create(getProject(), null, remotePaths);

    assertThatFile(sut.toLocalFile("noCgo.go")).hasWorkspacePath("noCgo.go");
    assertThatFile(sut.toLocalFile("_main/cgo.go")).hasWorkspacePath("cgo.go");
    assertThatFile(sut.toLocalFile("_main/collision1.go")).hasWorkspacePath("_main/collision1.go");
    assertThatFile(sut.toLocalFile("_main/collision2.go")).hasWorkspacePath("collision2.go");
    assertThatFile(sut.toLocalFile("_main/_main/collision2.go")).hasWorkspacePath("_main/collision2.go");
    assertThatFile(sut.toLocalFile("_main/nonExistent.go")).hasWorkspacePath("_main/nonExistent.go");
    assertThatFile(sut.toLocalFile("foobar/cgo.go")).hasWorkspacePath("foobar/cgo.go");
  }

  @Test
  public void testMatchesCgoTrimmedPath() {
    assertThat(cgoTrimmedPathsHandler.matchesCgoTrimmedPath("_main/cgo.go")).isTrue();
    assertThat(cgoTrimmedPathsHandler.matchesCgoTrimmedPath("nonCgo.go")).isFalse();
    assertThat(cgoTrimmedPathsHandler.matchesCgoTrimmedPath("foobar/cgo.go")).isFalse();
  }

  @Test
  public void testNormalizeCgoTrimmedPath() {
    assertThat(cgoTrimmedPathsHandler.normalizeCgoTrimmedPath("_main/cgo.go")).isEqualTo("cgo.go");
    assertThat(cgoTrimmedPathsHandler.normalizeCgoTrimmedPath("noCgo.go")).isEqualTo("noCgo.go");
    assertThat(cgoTrimmedPathsHandler.normalizeCgoTrimmedPath("_main/collision1.go")).isEqualTo("_main/collision1.go");
    assertThat(cgoTrimmedPathsHandler.normalizeCgoTrimmedPath("_main/collision2.go")).isEqualTo("collision2.go");
    assertThat(cgoTrimmedPathsHandler.normalizeCgoTrimmedPath("_main/_main/collision2.go")).isEqualTo("_main/collision2.go");
    assertThat(cgoTrimmedPathsHandler.normalizeCgoTrimmedPath("_main/nonExistent.go")).isEqualTo("_main/nonExistent.go");
  }
}
