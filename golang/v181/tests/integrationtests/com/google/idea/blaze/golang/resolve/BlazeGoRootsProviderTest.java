/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
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

import com.goide.project.GoRootsProvider;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.idea.blaze.base.BlazeIntegrationTestCase;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.ideinfo.GoIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetKey;
import com.google.idea.blaze.base.ideinfo.TargetMap;
import com.google.idea.blaze.base.ideinfo.TargetMapBuilder;
import com.google.idea.blaze.base.io.FileOperationProvider;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.MockBlazeProjectDataManager;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.model.primitives.WorkspaceType;
import com.google.idea.blaze.base.plugin.PluginUtils;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.base.sync.projectview.WorkspaceLanguageSettings;
import com.google.idea.blaze.golang.BlazeGoSupport;
import com.google.idea.common.experiments.ExperimentService;
import com.google.idea.common.experiments.MockExperimentService;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileVisitor;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Integration tests for {@link BlazeGoRootsProvider} */
@RunWith(JUnit4.class)
public class BlazeGoRootsProviderTest extends BlazeIntegrationTestCase {
  private MockFileOperationProvider fileOperationProvider;

  @Before
  public void init() {
    MockExperimentService experimentService = new MockExperimentService();
    experimentService.setExperiment(BlazeGoSupport.blazeGoSupportEnabled, true);
    registerApplicationComponent(ExperimentService.class, experimentService);
    fileOperationProvider = new MockFileOperationProvider();
    registerApplicationService(FileOperationProvider.class, fileOperationProvider);
  }

  @Test
  public void testGoPluginEnabled() {
    assertThat(PluginUtils.isPluginEnabled("org.jetbrains.plugins.go")).isTrue();
  }

  @Test
  public void testBuildGoRoot() {
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
                    .addSource(src("one/two/three/library.go"))
                    .setGoInfo(
                        GoIdeInfo.builder()
                            .addSources(
                                ImmutableList.of(
                                    src("one/two/library.go"), src("one/two/three/library.go")))
                            .setImportPath("prefix/one/two/library")))
            .build();

    BlazeProjectData projectData =
        new BlazeProjectData(
            0L,
            targetMap,
            null,
            null,
            null,
            location -> workspaceRoot.fileForPath(new WorkspacePath(location.getRelativePath())),
            new WorkspaceLanguageSettings(WorkspaceType.GO, ImmutableSet.of(LanguageClass.GO)),
            null,
            null);
    registerProjectService(
        BlazeProjectDataManager.class, new MockBlazeProjectDataManager(projectData));

    BlazeGoRootsProvider.createGoPathSourceRoot(getProject(), projectData);
    BlazeGoRootsProvider goRootsProvider =
        GoRootsProvider.EP_NAME.findExtension(BlazeGoRootsProvider.class);
    assertThat(goRootsProvider).isNotNull();
    Collection<VirtualFile> goRoots = goRootsProvider.getGoPathSourcesRoots(getProject(), null);
    VirtualFile goRoot = projectDataDirectory.findFileByRelativePath(".gopath");
    assertThat(goRoot).isNotNull();
    assertThat(goRoot.isDirectory()).isTrue();
    assertThat(goRoots).containsExactly(goRoot);
    assertIsDirectory(goRoot.findFileByRelativePath("prefix"));
    assertIsDirectory(goRoot.findFileByRelativePath("prefix/foo"));
    assertIsDirectory(goRoot.findFileByRelativePath("prefix/foo/bar"));
    assertIsDirectory(goRoot.findFileByRelativePath("prefix/foo/bar/binary"));
    assertIsSymbolicLink(
        goRoot.findFileByRelativePath("prefix/foo/bar/binary/binary_a7f1ab96.go"),
        "foo/bar/binary.go");
    assertIsDirectory(goRoot.findFileByRelativePath("prefix/one"));
    assertIsDirectory(goRoot.findFileByRelativePath("prefix/one/two"));
    assertIsDirectory(goRoot.findFileByRelativePath("prefix/one/two/library"));
    assertIsSymbolicLink(
        goRoot.findFileByRelativePath("prefix/one/two/library/library_81c8436f.go"),
        "one/two/library.go");
    assertIsSymbolicLink(
        goRoot.findFileByRelativePath("prefix/one/two/library/library_93e51efe.go"),
        "one/two/three/library.go");
  }

  private static void assertIsDirectory(VirtualFile directory) {
    assertThat(directory).isNotNull();
    assertThat(directory.isDirectory()).isTrue();
  }

  private void assertIsSymbolicLink(VirtualFile link, String workspaceTarget) {
    assertThat(link).isNotNull();
    assertThat(link.isDirectory()).isFalse();
    File resolvedLink = fileOperationProvider.readSymbolicLink(link);
    assertThat(resolvedLink).isNotNull();
    assertThat(resolvedLink)
        .isEqualTo(workspaceRoot.fileForPath(WorkspacePath.createIfValid(workspaceTarget)));
  }

  @Test
  public void testPackageToTargetMap() {
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
                null,
                null,
                new WorkspaceLanguageSettings(WorkspaceType.GO, ImmutableSet.of(LanguageClass.GO)),
                null,
                null)));

    assertThat(BlazeGoRootsProvider.getPackageToTargetMap(getProject()))
        .containsExactly(
            "prefix/foo/bar/binary",
            TargetKey.forPlainTarget(Label.create("//foo/bar:binary")),
            "prefix/one/two/library",
            TargetKey.forPlainTarget(Label.create("//one/two:library")));
  }

  private static ArtifactLocation src(String relativePath) {
    return ArtifactLocation.builder().setRelativePath(relativePath).setIsSource(true).build();
  }

  private class MockFileOperationProvider extends FileOperationProvider {
    private final Map<VirtualFile, File> symlinks = Maps.newHashMap();

    @Override
    public boolean mkdirs(File file) {
      return fileSystem.createDirectory(file.getPath()).isDirectory();
    }

    @Override
    public void createSymbolicLink(File link, File target) {
      symlinks.put(fileSystem.createFile(link.getPath()), target);
    }

    File readSymbolicLink(VirtualFile link) {
      return symlinks.get(link);
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
