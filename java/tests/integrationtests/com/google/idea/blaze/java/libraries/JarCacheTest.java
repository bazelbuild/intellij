/*
 * Copyright 2021 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.java.libraries;

import static com.google.common.truth.Truth.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.base.BlazeTestCase;
import com.google.idea.blaze.base.async.executor.BlazeExecutor;
import com.google.idea.blaze.base.async.executor.MockBlazeExecutor;
import com.google.idea.blaze.base.bazel.BazelBuildSystemProvider;
import com.google.idea.blaze.base.bazel.BuildSystemProvider;
import com.google.idea.blaze.base.command.buildresult.BlazeArtifact;
import com.google.idea.blaze.base.command.buildresult.RemoteOutputArtifact;
import com.google.idea.blaze.base.filecache.ArtifactState;
import com.google.idea.blaze.base.filecache.FileCache;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.ideinfo.LibraryArtifact;
import com.google.idea.blaze.base.io.FileOperationProvider;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.MockBlazeProjectDataBuilder;
import com.google.idea.blaze.base.model.SyncState;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.model.primitives.WorkspaceType;
import com.google.idea.blaze.base.prefetch.DefaultPrefetcher;
import com.google.idea.blaze.base.prefetch.RemoteArtifactPrefetcher;
import com.google.idea.blaze.base.projectview.ProjectView;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.projectview.section.Glob.GlobSet;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.OutputSink;
import com.google.idea.blaze.base.scope.output.PrintOutput;
import com.google.idea.blaze.base.settings.BlazeImportSettings;
import com.google.idea.blaze.base.settings.BlazeImportSettingsManager;
import com.google.idea.blaze.base.settings.BuildSystem;
import com.google.idea.blaze.base.sync.BlazeSyncPlugin;
import com.google.idea.blaze.base.sync.SyncMode;
import com.google.idea.blaze.base.sync.libraries.BlazeLibrarySorter;
import com.google.idea.blaze.base.sync.projectview.WorkspaceLanguageSettings;
import com.google.idea.blaze.base.sync.workspace.ArtifactLocationDecoder;
import com.google.idea.blaze.base.sync.workspace.MockArtifactLocationDecoder;
import com.google.idea.blaze.java.settings.BlazeJavaUserSettings;
import com.google.idea.blaze.java.sync.BlazeJavaSyncPlugin;
import com.google.idea.blaze.java.sync.importer.emptylibrary.EmptyJarTracker;
import com.google.idea.blaze.java.sync.model.BlazeJavaImportResult;
import com.google.idea.blaze.java.sync.model.BlazeJavaSyncData;
import com.google.idea.blaze.java.sync.model.BlazePluginProcessorJarLibrary;
import com.google.idea.common.experiments.ExperimentService;
import com.google.idea.common.experiments.MockExperimentService;
import com.intellij.openapi.util.io.FileUtil;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.file.Files;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.jetbrains.annotations.Nullable;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link com.google.idea.blaze.java.libraries.JarCache}. */
@RunWith(JUnit4.class)
public class JarCacheTest extends BlazeTestCase {
  @Rule public TemporaryFolder folder = new TemporaryFolder();
  private WorkspaceRoot workspaceRoot;
  private WritingOutputSink writingOutputSink;
  private BlazeContext context;
  private ArtifactLocationDecoder localArtifactLocationDecoder;
  private ArtifactLocationDecoder remoteArtifactLocationDecoder;

  @Override
  protected void initTest(Container applicationServices, Container projectServices) {
    writingOutputSink = new WritingOutputSink();
    context = new BlazeContext();
    context.addOutputSink(PrintOutput.class, writingOutputSink);
    workspaceRoot = new WorkspaceRoot(folder.getRoot());
    localArtifactLocationDecoder =
        new MockArtifactLocationDecoder() {
          @Override
          public File decode(ArtifactLocation artifactLocation) {
            return new File(workspaceRoot.directory(), artifactLocation.getRelativePath());
          }
        };

    remoteArtifactLocationDecoder =
        new MockArtifactLocationDecoder() {
          @Override
          public File decode(ArtifactLocation artifactLocation) {
            return new File(workspaceRoot.directory(), artifactLocation.getRelativePath());
          }

          @Override
          public BlazeArtifact resolveOutput(ArtifactLocation artifact) {
            if (artifact.isSource()) {
              return super.resolveOutput(artifact);
            }

            File file = new File(workspaceRoot.directory(), artifact.getRelativePath());
            // it's possible that remote artifact cannot be resolved e.g. it gets expired.
            // ArtifactLocationDecoderImpl will try to find it from local instead in such case.
            // We make this MockArtifactLocationDecoder same behavior as ArtifactLocationDecoderImpl
            return file.exists()
                ? new FakeRemoteOutputArtifact(file)
                : super.resolveOutput(artifact);
          }
        };
    BlazeImportSettingsManager blazeImportSettingsManager = new BlazeImportSettingsManager(project);
    try {
      File projectDataDirectory = folder.newFolder("projectdata");
      BlazeImportSettings dummyImportSettings =
          new BlazeImportSettings(
              "", "", projectDataDirectory.getAbsolutePath(), "", BuildSystem.Blaze);
      blazeImportSettingsManager.setImportSettings(dummyImportSettings);
    } catch (IOException e) {
      throw new AssertionError("Fail to create directory for test", e);
    }
    projectServices.register(BlazeImportSettingsManager.class, blazeImportSettingsManager);
    applicationServices.register(FileOperationProvider.class, new FileOperationProvider());
    applicationServices.register(RemoteArtifactPrefetcher.class, new DefaultPrefetcher());
    projectServices.register(JarCacheFolderProvider.class, new JarCacheFolderProvider(project));
    JarCache jarCache = new JarCache(project);
    jarCache.enableForTest();
    projectServices.register(JarCache.class, jarCache);
    registerExtensionPoint(BuildSystemProvider.EP_NAME, BuildSystemProvider.class)
        .registerExtension(
            new BazelBuildSystemProvider() {
              @Override
              public BuildSystem buildSystem() {
                return BuildSystem.Blaze;
              }
            });
    registerExtensionPoint(FileCache.EP_NAME, FileCache.class)
        .registerExtension(new JarCache.FileCacheAdapter());
    registerExtensionPoint(BlazeSyncPlugin.EP_NAME, BlazeSyncPlugin.class)
        .registerExtension(new BlazeJavaSyncPlugin());
    registerExtensionPoint(BlazeLibrarySorter.EP_NAME, BlazeLibrarySorter.class);
    applicationServices.register(BlazeJavaUserSettings.class, new BlazeJavaUserSettings());
    applicationServices.register(ExperimentService.class, new MockExperimentService());
    applicationServices.register(BlazeExecutor.class, new MockBlazeExecutor());
  }

  private static class FakeRemoteOutputArtifact implements RemoteOutputArtifact {
    private final File file;

    FakeRemoteOutputArtifact(File file) {
      this.file = file;
    }

    @Override
    public long getLength() {
      return this.file.length();
    }

    @Override
    public BufferedInputStream getInputStream() throws IOException {
      return new BufferedInputStream(new FileInputStream(file));
    }

    @Override
    public String getConfigurationMnemonic() {
      return "";
    }

    @Override
    public String getRelativePath() {
      return file.getPath();
    }

    @Nullable
    @Override
    public ArtifactState toArtifactState() {
      return null;
    }

    @Override
    public void prefetch() {}

    @Override
    public String getHashId() {
      return String.valueOf(FileUtil.fileHashCode(file));
    }

    @Override
    public long getSyncTimeMillis() {
      return 0;
    }
  }

  private ArtifactLocation generateArtifactLocation(String relativePath) {
    return ArtifactLocation.builder()
        .setRootExecutionPathFragment(workspaceRoot.directory().getAbsolutePath())
        .setRelativePath(relativePath)
        .setIsSource(false)
        .build();
  }

  @Test
  public void refresh_localArtifact_lintJarCached() throws IOException {
    testRefreshLintJarCached(localArtifactLocationDecoder);
  }

  /**
   * This test sets up blaze project data with a single java import result. It verifies that when
   * the file caches are refreshed, the jar cache correctly caches the lint jars.
   */
  @Test
  public void refresh_remoteArtifact_lintJarCached() throws IOException {
    testRefreshLintJarCached(remoteArtifactLocationDecoder);
  }

  private void testRefreshLintJarCached(ArtifactLocationDecoder decoder) throws IOException {
    // arrange: set up a project that have PluginProcessorJars
    String pluginProcessorJar = "pluginProcessor.jar";
    File jar = workspaceRoot.fileForPath(new WorkspacePath(pluginProcessorJar));
    try (ZipOutputStream zo = new ZipOutputStream(new FileOutputStream(jar))) {
      zo.putNextEntry(new ZipEntry("com/google/foo/gen/Gen.java"));
      zo.write("package gen; class Gen {}".getBytes(UTF_8));
      zo.closeEntry();
    }
    ArtifactLocation lintJarArtifactLocation = generateArtifactLocation(pluginProcessorJar);
    LibraryArtifact libraryArtifact =
        LibraryArtifact.builder().setInterfaceJar(lintJarArtifactLocation).build();

    BlazeJavaImportResult importResult =
        BlazeJavaImportResult.builder()
            .setContentEntries(ImmutableList.of())
            .setLibraries(ImmutableMap.of())
            .setBuildOutputJars(ImmutableList.of())
            .setJavaSourceFiles(ImmutableSet.of())
            .setSourceVersion(null)
            .setEmptyJarTracker(EmptyJarTracker.builder().build())
            .setPluginProcessorJars(
                ImmutableSet.of(new BlazePluginProcessorJarLibrary(libraryArtifact, null)))
            .build();
    BlazeJavaSyncData syncData =
        new BlazeJavaSyncData(importResult, new GlobSet(ImmutableList.of()));
    BlazeProjectData blazeProjectData =
        MockBlazeProjectDataBuilder.builder(workspaceRoot)
            .setWorkspaceLanguageSettings(
                new WorkspaceLanguageSettings(
                    WorkspaceType.JAVA, ImmutableSet.of(LanguageClass.JAVA)))
            .setSyncState(new SyncState.Builder().put(syncData).build())
            .setArtifactLocationDecoder(decoder)
            .build();

    // act: refresh all the file caches, which in turn will fetch the plugin processor jar to local
    FileCache.EP_NAME
        .extensions()
        .forEach(
            ep ->
                ep.onSync(
                    getProject(),
                    context,
                    ProjectViewSet.builder().add(ProjectView.builder().build()).build(),
                    blazeProjectData,
                    null,
                    SyncMode.INCREMENTAL));

    // assert: verify that the plugin processor jar was extracted from the cache, and that it is has
    // the right contents.
    File cacheDir = JarCacheFolderProvider.getInstance(project).getJarCacheFolder();

    assertThat(cacheDir.list()).hasLength(1);
    byte[] actualJarContent = Files.readAllBytes(cacheDir.listFiles()[0].toPath());
    byte[] expectedJarContent = Files.readAllBytes(jar.toPath());
    assertThat(actualJarContent).isEqualTo(expectedJarContent);
  }

  private static class WritingOutputSink implements OutputSink<PrintOutput> {

    private final Writer writer = new StringWriter();

    @Override
    public Propagation onOutput(PrintOutput output) {
      try {
        writer.write(output.getText());
        return Propagation.Continue;
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }

    public String getMessages() {
      return writer.toString();
    }
  }
}
