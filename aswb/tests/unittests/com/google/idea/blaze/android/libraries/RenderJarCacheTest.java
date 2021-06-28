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
package com.google.idea.blaze.android.libraries;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static java.util.Arrays.stream;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.android.libraries.RenderJarCache.FileCacheAdapter;
import com.google.idea.blaze.android.projectsystem.BlazeClassFileFinderFactory;
import com.google.idea.blaze.android.projectsystem.RenderJarClassFileFinder;
import com.google.idea.blaze.base.MockProjectViewManager;
import com.google.idea.blaze.base.async.executor.BlazeExecutor;
import com.google.idea.blaze.base.async.executor.MockBlazeExecutor;
import com.google.idea.blaze.base.command.buildresult.BlazeArtifact;
import com.google.idea.blaze.base.command.buildresult.LocalFileOutputArtifact;
import com.google.idea.blaze.base.filecache.FileCache;
import com.google.idea.blaze.base.ideinfo.AndroidIdeInfo;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetMap;
import com.google.idea.blaze.base.ideinfo.TargetMapBuilder;
import com.google.idea.blaze.base.io.FileOperationProvider;
import com.google.idea.blaze.base.model.MockBlazeProjectDataBuilder;
import com.google.idea.blaze.base.model.MockBlazeProjectDataManager;
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.model.primitives.Kind.ApplicationState;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.prefetch.DefaultPrefetcher;
import com.google.idea.blaze.base.prefetch.RemoteArtifactPrefetcher;
import com.google.idea.blaze.base.projectview.ProjectViewManager;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.projectview.parser.ProjectViewParser;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.ErrorCollector;
import com.google.idea.blaze.base.scope.OutputSink;
import com.google.idea.blaze.base.scope.output.IssueOutput;
import com.google.idea.blaze.base.scope.output.PrintOutput;
import com.google.idea.blaze.base.settings.BlazeImportSettings;
import com.google.idea.blaze.base.settings.BlazeImportSettingsManager;
import com.google.idea.blaze.base.settings.BuildSystem;
import com.google.idea.blaze.base.sync.BlazeSyncPlugin;
import com.google.idea.blaze.base.sync.SyncMode;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.base.sync.workspace.ArtifactLocationDecoder;
import com.google.idea.blaze.base.sync.workspace.MockArtifactLocationDecoder;
import com.google.idea.blaze.base.sync.workspace.WorkspacePathResolverImpl;
import com.google.idea.blaze.java.AndroidBlazeRules;
import com.google.idea.blaze.java.AndroidBlazeRules.RuleTypes;
import com.google.idea.blaze.java.sync.BlazeJavaSyncPlugin;
import com.google.idea.common.experiments.ExperimentService;
import com.google.idea.common.experiments.MockExperimentService;
import com.google.idea.testing.IntellijRule;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.file.Paths;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link RenderJarCache} */
@RunWith(JUnit4.class)
public class RenderJarCacheTest {
  @Rule public final IntellijRule intellijRule = new IntellijRule();
  @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

  private ErrorCollector errorCollector;
  private WritingOutputSink outputSink;
  private BlazeContext context;
  private WorkspaceRoot workspaceRoot;
  private ArtifactLocationDecoder artifactLocationDecoder;
  private MockProjectViewManager projectViewManager;

  @Before
  public void initTest() throws IOException {
    errorCollector = new ErrorCollector();
    outputSink = new WritingOutputSink();
    context = new BlazeContext();
    context.addOutputSink(PrintOutput.class, outputSink);
    workspaceRoot = new WorkspaceRoot(temporaryFolder.getRoot());
    artifactLocationDecoder =
        new MockArtifactLocationDecoder() {
          @Override
          public File decode(ArtifactLocation artifactLocation) {
            return new File(workspaceRoot.directory(), artifactLocation.getRelativePath());
          }
        };

    registerMockBlazeImportSettings();

    intellijRule.registerProjectService(
        RenderJarCache.class, new RenderJarCache(intellijRule.getProject()));

    intellijRule.registerApplicationService(
        FileOperationProvider.class, new FileOperationProvider());
    intellijRule.registerApplicationService(
        RemoteArtifactPrefetcher.class, new DefaultPrefetcher());

    intellijRule.registerExtensionPoint(FileCache.EP_NAME, FileCache.class);
    intellijRule.registerExtension(FileCache.EP_NAME, new FileCacheAdapter());

    // Required to enable RenderJarClassFileFinder
    MockExperimentService experimentService = new MockExperimentService();
    experimentService.setExperimentString(
        BlazeClassFileFinderFactory.CLASS_FILE_FINDER_NAME,
        RenderJarClassFileFinder.CLASS_FINDER_KEY);
    intellijRule.registerApplicationService(ExperimentService.class, experimentService);

    // Setup needed for setting a projectview
    intellijRule.registerExtensionPoint(BlazeSyncPlugin.EP_NAME, BlazeSyncPlugin.class);
    intellijRule.registerExtension(BlazeSyncPlugin.EP_NAME, new BlazeJavaSyncPlugin());

    // RenderJarCache looks at targets of `Kind`s with LanguageClass.ANDROID
    // so we need to setup the framework for fetching a target's `Kind`
    intellijRule.registerExtensionPoint(Kind.Provider.EP_NAME, Kind.Provider.class);
    intellijRule.registerExtension(Kind.Provider.EP_NAME, new AndroidBlazeRules());
    intellijRule.registerApplicationService(ApplicationState.class, new ApplicationState());

    // registered because `RenderJarCache` uses it to filter source targets
    projectViewManager = new MockProjectViewManager();
    intellijRule.registerProjectService(ProjectViewManager.class, projectViewManager);

    intellijRule.registerApplicationService(BlazeExecutor.class, new MockBlazeExecutor());

    setupProjectData();
    setProjectView(
        "directories:",
        "  com/foo/bar/baz",
        "  com/foo/bar/qux",
        "targets:",
        "  //com/foo/bar/baz:baz",
        "  //com/foo/bar/qux:quz");
  }

  /** Test that new JARs are copied to the cache */
  @Test
  public void incrementalSync_emptyCache_successfullyAddsJars() {
    File cacheDir = RenderJarCache.getInstance(intellijRule.getProject()).getCacheDir();
    assertThat(cacheDir.mkdirs()).isTrue();
    assertThat(cacheDir.list()).hasLength(0); // ensure cache directory is empty to begin with

    FileCache.EP_NAME
        .extensions()
        .forEach(
            ep ->
                ep.onSync(
                    intellijRule.getProject(),
                    context,
                    projectViewManager.getProjectViewSet(),
                    BlazeProjectDataManager.getInstance(intellijRule.getProject())
                        .getBlazeProjectData(),
                    null,
                    SyncMode.INCREMENTAL));

    assertThat(cacheDir.list()).hasLength(2);
    assertThat(stream(cacheDir.listFiles()).map(File::lastModified))
        .containsExactly(100000L, 100000L);

    String messages = outputSink.getMessages();
    assertThat(messages).contains("Copied 2 Render JARs");
  }

  /** Test that a JAR no longer in build is removed after an incremental sync */
  @Test
  public void incrementalSync_obsoleteJarInCache_successfullyRemoved() throws IOException {
    File cacheDir = RenderJarCache.getInstance(intellijRule.getProject()).getCacheDir();
    assertThat(cacheDir.mkdirs()).isTrue();

    File obsoleteJar = new File(cacheDir, "obsoleteRender.jar");
    assertThat(obsoleteJar.createNewFile()).isTrue();
    assertThat(obsoleteJar.setLastModified(100L)).isTrue();

    FileCache.EP_NAME
        .extensions()
        .forEach(
            ep ->
                ep.onSync(
                    intellijRule.getProject(),
                    context,
                    projectViewManager.getProjectViewSet(),
                    BlazeProjectDataManager.getInstance(intellijRule.getProject())
                        .getBlazeProjectData(),
                    null,
                    SyncMode.INCREMENTAL));

    assertThat(cacheDir.list()).hasLength(2);
    assertThat(stream(cacheDir.listFiles()).map(File::lastModified))
        .containsExactly(100000L, 100000L);

    String messages = outputSink.getMessages();
    assertThat(messages).contains("Copied 2 Render JARs");
    assertThat(messages).contains("Removed 1 Render JARs");
  }

  /** Test that an outdated JAR in cache is replaced by the newer version from build */
  @Test
  public void incrementalSync_outdatedJarInCache_successfullyReplaced() throws IOException {
    File cacheDir = RenderJarCache.getInstance(intellijRule.getProject()).getCacheDir();
    assertThat(cacheDir.mkdirs()).isTrue();

    String outdatedJarName =
        RenderJarCache.cacheKeyForJar(
            artifactLocationDecoder.resolveOutput(
                getArtifactLocation("com/foo/bar/baz/baz_render_jar.jar")));
    File outdatedJarFile = new File(cacheDir, outdatedJarName);
    assertThat(outdatedJarFile.createNewFile()).isTrue();
    assertThat(outdatedJarFile.setLastModified(100L)).isTrue();

    FileCache.EP_NAME
        .extensions()
        .forEach(
            ep ->
                ep.onSync(
                    intellijRule.getProject(),
                    context,
                    projectViewManager.getProjectViewSet(),
                    BlazeProjectDataManager.getInstance(intellijRule.getProject())
                        .getBlazeProjectData(),
                    null,
                    SyncMode.INCREMENTAL));

    assertThat(cacheDir.list()).hasLength(2);
    assertThat(stream(cacheDir.listFiles()).map(File::lastModified))
        .containsExactly(100000L, 100000L);

    String messages = outputSink.getMessages();
    assertThat(messages).contains("Copied 2 Render JARs");
    assertThat(messages).doesNotContainMatch("Removed \\d+ Render JARs");
  }

  /**
   * Test that a partial sync does not remove JARs not in build information. Partial sync might be
   * missing information about JARs that are built by an incremental/full sync, so the cache should
   * not delete JARs after Partial Syncs
   */
  @Test
  public void partialSync_refreshDoesNotRemoveObsoleteJar() throws IOException {
    File cacheDir = RenderJarCache.getInstance(intellijRule.getProject()).getCacheDir();
    assertThat(cacheDir.mkdirs()).isTrue();

    File obsoleteJar = new File(cacheDir, "obsoleteRender.jar");
    assertThat(obsoleteJar.createNewFile()).isTrue();
    assertThat(obsoleteJar.setLastModified(100L)).isTrue();

    FileCache.EP_NAME
        .extensions()
        .forEach(
            ep ->
                ep.onSync(
                    intellijRule.getProject(),
                    context,
                    projectViewManager.getProjectViewSet(),
                    BlazeProjectDataManager.getInstance(intellijRule.getProject())
                        .getBlazeProjectData(),
                    null,
                    SyncMode.PARTIAL));

    assertThat(cacheDir.list()).hasLength(3);
    assertThat(stream(cacheDir.listFiles()).map(File::lastModified))
        .containsExactly(100L, 100000L, 100000L);

    String messages = outputSink.getMessages();
    assertThat(messages).contains("Copied 2 Render JARs");
    assertThat(messages).doesNotContainMatch("Removed \\d+ Render JARs");
  }

  /** Test that a cache refresh pull in the latest version of the JARs from the build */
  @Test
  public void cacheRefresh_updatesOutdatedJars() throws IOException {
    File cacheDir = RenderJarCache.getInstance(intellijRule.getProject()).getCacheDir();
    assertThat(cacheDir.mkdirs()).isTrue();

    ImmutableList<String> outdatedJarNames =
        ImmutableList.of(
            RenderJarCache.cacheKeyForJar(
                artifactLocationDecoder.resolveOutput(
                    getArtifactLocation("com/foo/bar/baz/baz_render_jar.jar"))),
            RenderJarCache.cacheKeyForJar(
                artifactLocationDecoder.resolveOutput(
                    getArtifactLocation("com/foo/bar/qux/qux_render_jar.jar"))));

    for (String jarName : outdatedJarNames) {
      File jarFile = new File(cacheDir, jarName);
      assertThat(jarFile.createNewFile()).isTrue();
      assertThat(jarFile.setLastModified(100L)).isTrue();
    }

    FileCache.EP_NAME
        .extensions()
        .forEach(ep -> ep.refreshFiles(intellijRule.getProject(), context));

    assertThat(cacheDir.list()).hasLength(2);
    assertThat(stream(cacheDir.listFiles()).map(File::lastModified))
        .containsExactly(100000L, 100000L);

    String messages = outputSink.getMessages();
    assertThat(messages).contains("Copied 2 Render JARs");
    assertThat(messages).doesNotContainMatch("Removed \\d+ Render JARs");
  }

  /**
   * Sets up a mock {@link com.google.devtools.intellij.model.ProjectData} and creates the render
   * JARs in File System
   */
  private void setupProjectData() throws IOException {
    TargetMap targetMap =
        TargetMapBuilder.builder()
            .addTarget(
                TargetIdeInfo.builder()
                    .setLabel("//com/foo/bar/baz:baz")
                    .setAndroidInfo(
                        AndroidIdeInfo.builder()
                            .setRenderResolveJar(
                                getArtifactLocation("com/foo/bar/baz/baz_render_jar.jar")))
                    .setKind(RuleTypes.KT_ANDROID_LIBRARY_HELPER.getKind()))
            .addTarget(
                TargetIdeInfo.builder()
                    .setLabel("//com/foo/bar/qux:qux")
                    .setAndroidInfo(
                        AndroidIdeInfo.builder()
                            .setRenderResolveJar(
                                getArtifactLocation("com/foo/bar/qux/qux_render_jar.jar")))
                    .setKind(RuleTypes.KT_ANDROID_LIBRARY_HELPER.getKind())
                    .build())
            .build();
    intellijRule.registerProjectService(
        BlazeProjectDataManager.class,
        new MockBlazeProjectDataManager(
            MockBlazeProjectDataBuilder.builder()
                .setArtifactLocationDecoder(artifactLocationDecoder)
                .setTargetMap(targetMap)
                .build()));

    // Create baz_render_jar.jar in FS
    BlazeArtifact bazRenderJar =
        artifactLocationDecoder.resolveOutput(
            getArtifactLocation("com/foo/bar/baz/baz_render_jar.jar"));
    File bazRenderJarFile = ((LocalFileOutputArtifact) bazRenderJar).getFile();
    assertThat(Paths.get(bazRenderJarFile.getParent()).toFile().mkdirs()).isTrue();
    assertThat(bazRenderJarFile.createNewFile()).isTrue();
    assertThat(bazRenderJarFile.setLastModified(100000L)).isTrue();

    // Create qux_render_jar.jar in FS
    BlazeArtifact quxRenderJar =
        artifactLocationDecoder.resolveOutput(
            getArtifactLocation("com/foo/bar/qux/qux_render_jar.jar"));
    File quxRenderJarFile = ((LocalFileOutputArtifact) quxRenderJar).getFile();
    assertThat(Paths.get(quxRenderJarFile.getParent()).toFile().mkdirs()).isTrue();
    assertThat(quxRenderJarFile.createNewFile()).isTrue();
    assertThat(quxRenderJarFile.setLastModified(100000L)).isTrue();
  }

  /** Sets up {@link BlazeImportSettings} with a temporary directory for project data. */
  private void registerMockBlazeImportSettings() throws IOException {
    BlazeImportSettingsManager importSettingsManager =
        new BlazeImportSettingsManager(intellijRule.getProject());
    File projectDataDir = temporaryFolder.newFolder("project_data");
    importSettingsManager.setImportSettings(
        new BlazeImportSettings(
            /*workspaceRoot=*/ "",
            intellijRule.getProject().getName(),
            projectDataDir.getAbsolutePath(),
            /*projectViewFile=*/ "",
            BuildSystem.Blaze));
    intellijRule.registerProjectService(BlazeImportSettingsManager.class, importSettingsManager);
  }

  /** Utility method to create an {@link ArtifactLocation} for the given relative path */
  private ArtifactLocation getArtifactLocation(String relativePath) {
    return ArtifactLocation.builder()
        .setRootExecutionPathFragment(workspaceRoot.directory().getAbsolutePath())
        .setRelativePath(relativePath)
        .setIsSource(false)
        .build();
  }

  /**
   * Updates the projectview for filtering source targets and clears the sync cache. Will fail if
   * {@code contents} is incorrectly formatted as a projectview file
   */
  private void setProjectView(String... contents) {
    BlazeContext context = new BlazeContext();
    context.addOutputSink(IssueOutput.class, errorCollector);
    ProjectViewParser projectViewParser =
        new ProjectViewParser(context, new WorkspacePathResolverImpl(workspaceRoot));
    projectViewParser.parseProjectView(Joiner.on("\n").join(contents));

    ProjectViewSet result = projectViewParser.getResult();
    assertThat(result.getProjectViewFiles()).isNotEmpty();
    errorCollector.assertNoIssues();

    projectViewManager.setProjectView(result);
  }

  /** Utility class to log output from {@link RenderJarCache} for assertions */
  private static class WritingOutputSink implements OutputSink<PrintOutput> {

    private final Writer stringWriter = new StringWriter();
    private final PrintWriter writer = new PrintWriter(stringWriter);

    @Override
    public Propagation onOutput(PrintOutput output) {
      writer.println(output.getText());
      return Propagation.Continue;
    }

    public String getMessages() {
      return stringWriter.toString();
    }
  }
}
