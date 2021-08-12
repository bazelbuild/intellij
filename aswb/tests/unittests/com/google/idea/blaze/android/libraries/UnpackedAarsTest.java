/*
 * Copyright 2020 The Bazel Authors. All rights reserved.
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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.truth.Truth.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.android.libraries.UnpackedAars.FileCacheAdapter;
import com.google.idea.blaze.android.sync.BlazeAndroidSyncPlugin;
import com.google.idea.blaze.android.sync.model.AarLibrary;
import com.google.idea.blaze.android.sync.model.AarLibraryFactory;
import com.google.idea.blaze.android.sync.model.AndroidSdkPlatform;
import com.google.idea.blaze.android.sync.model.BlazeAndroidImportResult;
import com.google.idea.blaze.android.sync.model.BlazeAndroidSyncData;
import com.google.idea.blaze.base.BlazeTestCase;
import com.google.idea.blaze.base.filecache.FileCache;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.ideinfo.LibraryArtifact;
import com.google.idea.blaze.base.io.FileOperationProvider;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.LibraryKey;
import com.google.idea.blaze.base.model.MockBlazeProjectDataBuilder;
import com.google.idea.blaze.base.model.SyncState;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.model.primitives.WorkspaceType;
import com.google.idea.blaze.base.prefetch.DefaultPrefetcher;
import com.google.idea.blaze.base.prefetch.RemoteArtifactPrefetcher;
import com.google.idea.blaze.base.projectview.ProjectView;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
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
import com.google.idea.common.experiments.ExperimentService;
import com.google.idea.common.experiments.MockExperimentService;
import com.intellij.openapi.util.io.FileUtil;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link com.google.idea.blaze.android.libraries.UnpackedAars}. */
@RunWith(JUnit4.class)
public class UnpackedAarsTest extends BlazeTestCase {
  @Rule public TemporaryFolder folder = new TemporaryFolder();
  private WorkspaceRoot workspaceRoot;
  private WritingOutputSink writingOutputSink;
  private BlazeContext context;
  private ArtifactLocationDecoder artifactLocationDecoder;
  private static final String STRINGS_XML_CONTENT =
      "<?xml version=\"1.0\" encoding=\"utf-8\"?>"
          + "<resources>"
          + "    <string name=\"appString\">Hello from app</string>"
          + "</resources>";

  private static final String COLORS_XML_CONTENT =
      "<?xml version=\"1.0\" encoding=\"utf-8\"?>"
          + "<resources>"
          + "    <color name=\"quantum_black_100\">#000000</color>"
          + "</resources>";

  private static final String CONFLICT_COLORS_XML_CONTENT =
      "<?xml version=\"1.0\" encoding=\"utf-8\"?>"
          + "<resources>"
          + "    <color name=\"quantum_black_200\">#100000</color>"
          + "</resources>";

  @Override
  protected void initTest(Container applicationServices, Container projectServices) {
    writingOutputSink = new WritingOutputSink();
    context = new BlazeContext();
    context.addOutputSink(PrintOutput.class, writingOutputSink);
    workspaceRoot = new WorkspaceRoot(folder.getRoot());
    artifactLocationDecoder =
        new MockArtifactLocationDecoder() {
          @Override
          public File decode(ArtifactLocation artifactLocation) {
            return new File(workspaceRoot.directory(), artifactLocation.getRelativePath());
          }
        };
    projectServices.register(
        BlazeImportSettingsManager.class, new BlazeImportSettingsManager(project));
    try {
      File projectDataDirectory = folder.newFolder("projectdata");
      BlazeImportSettings dummyImportSettings =
          new BlazeImportSettings(
              "", "", projectDataDirectory.getAbsolutePath(), "", BuildSystem.Bazel);
      BlazeImportSettingsManager.getInstance(project).setImportSettings(dummyImportSettings);
    } catch (IOException e) {
      throw new AssertionError("Fail to create directory for test", e);
    }
    applicationServices.register(FileOperationProvider.class, new FileOperationProvider());
    applicationServices.register(RemoteArtifactPrefetcher.class, new DefaultPrefetcher());
    projectServices.register(UnpackedAars.class, new UnpackedAars(project));
    registerExtensionPoint(FileCache.EP_NAME, FileCache.class)
        .registerExtension(new FileCacheAdapter());
    registerExtensionPoint(BlazeSyncPlugin.EP_NAME, BlazeSyncPlugin.class)
        .registerExtension(new BlazeAndroidSyncPlugin());
    registerExtensionPoint(BlazeLibrarySorter.EP_NAME, BlazeLibrarySorter.class);
    applicationServices.register(ExperimentService.class, new MockExperimentService());
  }

  private ArtifactLocation generateArtifactLocation(String relativePath) {
    return ArtifactLocation.builder()
        .setRootExecutionPathFragment(workspaceRoot.directory().getAbsolutePath())
        .setRelativePath(relativePath)
        .setIsSource(false)
        .build();
  }

  /**
   * When new aar files are not accessible, unpacked aar should still get completed. It will remove
   * cached aars but not included in current build (out of date aars) from cache and print out how
   * many aars has been removed in context.
   */
  @Test
  public void testRefresh_fileNotExist_success() throws IOException {
    // aars cached last time but not included in current build output. It should be removed after
    // current sync get finished.
    UnpackedAars unpackedAars = UnpackedAars.getInstance(project);
    File aarCacheDir = unpackedAars.getCacheDir();
    new File(aarCacheDir, "outOfDate.aar").mkdirs();

    // non-existent aar. It's not expected but for some corner cases, aars may not be accessible
    // e.g. objfs has been expired which cannot be read any more.
    String resourceAar = "resource.aar";
    ArtifactLocation resourceAarArtifactLocation = generateArtifactLocation(resourceAar);
    AarLibrary resourceAarLibrary = AarLibraryFactory.create(resourceAarArtifactLocation, null);

    BlazeAndroidImportResult importResult =
        new BlazeAndroidImportResult(
            ImmutableList.of(),
            ImmutableMap.of(
                LibraryKey.libraryNameFromArtifactLocation(resourceAarArtifactLocation),
                resourceAarLibrary),
            ImmutableList.of(),
            ImmutableList.of());
    BlazeAndroidSyncData syncData =
        new BlazeAndroidSyncData(importResult, new AndroidSdkPlatform("stable", 15));
    BlazeProjectData blazeProjectData =
        MockBlazeProjectDataBuilder.builder(workspaceRoot)
            .setWorkspaceLanguageSettings(
                new WorkspaceLanguageSettings(WorkspaceType.ANDROID, ImmutableSet.of()))
            .setSyncState(new SyncState.Builder().put(syncData).build())
            .setArtifactLocationDecoder(artifactLocationDecoder)
            .build();
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

    assertThat(aarCacheDir.list()).hasLength(0);
    String messages = writingOutputSink.getMessages();
    assertThat(messages).doesNotContain("Copied 1 AARs");
    assertThat(messages).contains("Removed 1 AARs");
  }

  @Test
  public void testRefresh_success() throws IOException {
    // aars cached last time but not included in current build output. It should be removed after
    // current sync get finished.
    UnpackedAars unpackedAars = UnpackedAars.getInstance(project);
    File aarCacheDir = unpackedAars.getCacheDir();
    String stringsXmlRelativePath = "res/values/strings.xml";
    new File(aarCacheDir, "outOFDate.aar").mkdirs();

    // new aar without jar files
    String resourceAar = "resource.aar";
    AarLibraryFileBuilder.aar(workspaceRoot, resourceAar)
        .src(stringsXmlRelativePath, ImmutableList.of(STRINGS_XML_CONTENT))
        .build();
    ArtifactLocation resourceAarArtifactLocation = generateArtifactLocation(resourceAar);
    AarLibrary resourceAarLibrary = AarLibraryFactory.create(resourceAarArtifactLocation, null);

    // new aar with jar files
    String importedAar = "import.aar";
    String importedAarJar = "importAar.jar";
    String colorsXmlRelativePath = "res/values/colors.xml";
    AarLibraryFileBuilder.aar(workspaceRoot, importedAar)
        .src(colorsXmlRelativePath, ImmutableList.of(COLORS_XML_CONTENT))
        .build();
    File jar = workspaceRoot.fileForPath(new WorkspacePath(importedAarJar));
    try (ZipOutputStream zo = new ZipOutputStream(new FileOutputStream(jar))) {
      zo.putNextEntry(new ZipEntry("com/google/foo/gen/Gen.java"));
      zo.write("package gen; class Gen {}".getBytes(UTF_8));
      zo.closeEntry();
    }
    ArtifactLocation importedAarArtifactLocation = generateArtifactLocation(importedAar);
    ArtifactLocation jarArtifactLocation = generateArtifactLocation(importedAarJar);
    LibraryArtifact libraryArtifact =
        LibraryArtifact.builder().setInterfaceJar(jarArtifactLocation).build();
    AarLibrary importedAarLibrary =
        AarLibraryFactory.create(libraryArtifact, importedAarArtifactLocation, null);

    BlazeAndroidImportResult importResult =
        new BlazeAndroidImportResult(
            ImmutableList.of(),
            ImmutableMap.of(
                LibraryKey.libraryNameFromArtifactLocation(resourceAarArtifactLocation),
                resourceAarLibrary,
                LibraryKey.libraryNameFromArtifactLocation(importedAarArtifactLocation),
                importedAarLibrary),
            ImmutableList.of(),
            ImmutableList.of());
    BlazeAndroidSyncData syncData =
        new BlazeAndroidSyncData(importResult, new AndroidSdkPlatform("stable", 15));
    BlazeProjectData blazeProjectData =
        MockBlazeProjectDataBuilder.builder(workspaceRoot)
            .setWorkspaceLanguageSettings(
                new WorkspaceLanguageSettings(WorkspaceType.ANDROID, ImmutableSet.of()))
            .setSyncState(new SyncState.Builder().put(syncData).build())
            .setArtifactLocationDecoder(artifactLocationDecoder)
            .build();
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

    assertThat(aarCacheDir.list()).hasLength(4);
    File mergedResourceAarDir = unpackedAars.getAarDir(resourceAarLibrary);
    File mergedImportedAarDir = unpackedAars.getAarDir(importedAarLibrary);
    File resourceAarDir =
        new File(
            unpackedAars.getCacheDir(),
            UnpackedAarUtils.getAarDirName(
                artifactLocationDecoder.resolveOutput(resourceAarLibrary.aarArtifact)));
    File importedAarDir =
        new File(
            unpackedAars.getCacheDir(),
            UnpackedAarUtils.getAarDirName(
                artifactLocationDecoder.resolveOutput(importedAarLibrary.aarArtifact)));
    assertThat(aarCacheDir.listFiles())
        .asList()
        .containsExactly(
            mergedResourceAarDir, mergedImportedAarDir, resourceAarDir, importedAarDir);
    assertThat(mergedResourceAarDir.list()).asList().containsExactly("res");
    assertThat(mergedImportedAarDir.list()).asList().containsExactly("res");

    // assert two aars does not merged into one

    assertThat(listFilesRecursively(UnpackedAarUtils.getResDir(mergedResourceAarDir)))
        .containsExactly(mergedResourceAarDir.toPath().resolve(stringsXmlRelativePath));
    assertThat(listFilesRecursively(UnpackedAarUtils.getResDir(mergedImportedAarDir)))
        .containsExactly(mergedImportedAarDir.toPath().resolve(colorsXmlRelativePath));
    assertThat(
            new String(
                Files.readAllBytes(new File(mergedResourceAarDir, stringsXmlRelativePath).toPath()),
                UTF_8))
        .isEqualTo(STRINGS_XML_CONTENT);
    assertThat(
            new String(
                Files.readAllBytes(new File(mergedImportedAarDir, colorsXmlRelativePath).toPath()),
                UTF_8))
        .isEqualTo(COLORS_XML_CONTENT);

    // assert each single aar is unpacked and copied correctly
    assertThat(listFilesRecursively(UnpackedAarUtils.getResDir(resourceAarDir)))
        .containsExactly(resourceAarDir.toPath().resolve(stringsXmlRelativePath));
    assertThat(listFilesRecursively(UnpackedAarUtils.getResDir(importedAarDir)))
        .containsExactly(importedAarDir.toPath().resolve(colorsXmlRelativePath));
    assertThat(
            new String(
                Files.readAllBytes(new File(resourceAarDir, stringsXmlRelativePath).toPath()),
                UTF_8))
        .isEqualTo(STRINGS_XML_CONTENT);
    assertThat(
            new String(
                Files.readAllBytes(new File(importedAarDir, colorsXmlRelativePath).toPath()),
                UTF_8))
        .isEqualTo(COLORS_XML_CONTENT);
    byte[] actualJarContent =
        Files.readAllBytes(new File(importedAarDir, "jars/classes_and_libs_merged.jar").toPath());
    byte[] expectedJarContent = Files.readAllBytes(jar.toPath());
    assertThat(actualJarContent).isEqualTo(expectedJarContent);

    String messages = writingOutputSink.getMessages();
    assertThat(messages).contains("Copied 2 AARs");
    assertThat(messages).contains("Removed 1 AARs");
  }

  @Test
  public void testRefresh_mergeLibraryWithSameResourcePackageName() throws IOException {
    // aars cached last time but not included in current build output. It should be removed after
    // current sync get finished.
    UnpackedAars unpackedAars = UnpackedAars.getInstance(project);
    File aarCacheDir = unpackedAars.getCacheDir();

    // new aar without jar files
    String resourceAar = "resource.aar";
    String stringsXmlRelativePath = "res/values/strings.xml";
    AarLibraryFileBuilder.aar(workspaceRoot, resourceAar)
        .src(stringsXmlRelativePath, ImmutableList.of(STRINGS_XML_CONTENT))
        .build();
    ArtifactLocation resourceAarArtifactLocation = generateArtifactLocation(resourceAar);
    AarLibrary resourceAarLibrary =
        AarLibraryFactory.create(resourceAarArtifactLocation, "com.foo");

    // new aar with jar files
    String anotherResourceAar = "another_resource.aar";
    String colorsXmlRelativePath = "res/values/colors.xml";
    AarLibraryFileBuilder.aar(workspaceRoot, anotherResourceAar)
        .src(colorsXmlRelativePath, ImmutableList.of(COLORS_XML_CONTENT))
        .build();
    ArtifactLocation anotherResourceAarArtifactLocation =
        generateArtifactLocation(anotherResourceAar);
    AarLibrary anotherResourceAarLibrary =
        AarLibraryFactory.create(anotherResourceAarArtifactLocation, "com.foo");

    BlazeAndroidImportResult importResult =
        new BlazeAndroidImportResult(
            ImmutableList.of(),
            ImmutableMap.of(
                LibraryKey.libraryNameFromArtifactLocation(resourceAarArtifactLocation),
                resourceAarLibrary,
                LibraryKey.libraryNameFromArtifactLocation(anotherResourceAarArtifactLocation),
                anotherResourceAarLibrary),
            ImmutableList.of(),
            ImmutableList.of());
    BlazeAndroidSyncData syncData =
        new BlazeAndroidSyncData(importResult, new AndroidSdkPlatform("stable", 15));
    BlazeProjectData blazeProjectData =
        MockBlazeProjectDataBuilder.builder(workspaceRoot)
            .setWorkspaceLanguageSettings(
                new WorkspaceLanguageSettings(WorkspaceType.ANDROID, ImmutableSet.of()))
            .setSyncState(new SyncState.Builder().put(syncData).build())
            .setArtifactLocationDecoder(artifactLocationDecoder)
            .build();
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

    assertThat(aarCacheDir.list()).hasLength(3);
    File mergedResourceAarDir = unpackedAars.getAarDir(resourceAarLibrary);
    File mergedAnotherResourceAarDir = unpackedAars.getAarDir(anotherResourceAarLibrary);
    assertThat(mergedResourceAarDir).isEqualTo(mergedAnotherResourceAarDir);
    File resourceAarDir =
        new File(
            unpackedAars.getCacheDir(),
            UnpackedAarUtils.getAarDirName(
                artifactLocationDecoder.resolveOutput(resourceAarLibrary.aarArtifact)));
    File anotherResourceAarDir =
        new File(
            unpackedAars.getCacheDir(),
            UnpackedAarUtils.getAarDirName(
                artifactLocationDecoder.resolveOutput(anotherResourceAarLibrary.aarArtifact)));
    assertThat(aarCacheDir.listFiles())
        .asList()
        .containsExactly(mergedResourceAarDir, resourceAarDir, anotherResourceAarDir);

    // assert two aars get merged correctly
    assertThat(mergedResourceAarDir.list()).asList().containsExactly("res");
    assertThat(listFilesRecursively(UnpackedAarUtils.getResDir(mergedResourceAarDir)))
        .containsExactly(
            mergedResourceAarDir.toPath().resolve(stringsXmlRelativePath),
            mergedResourceAarDir.toPath().resolve(colorsXmlRelativePath));
    assertThat(
            new String(
                Files.readAllBytes(new File(mergedResourceAarDir, stringsXmlRelativePath).toPath()),
                UTF_8))
        .isEqualTo(STRINGS_XML_CONTENT);
    assertThat(
            new String(
                Files.readAllBytes(new File(mergedResourceAarDir, colorsXmlRelativePath).toPath()),
                UTF_8))
        .isEqualTo(COLORS_XML_CONTENT);

    // assert each single aar is unpacked and copied correctly
    assertThat(listFilesRecursively(UnpackedAarUtils.getResDir(resourceAarDir)))
        .containsExactly(resourceAarDir.toPath().resolve(stringsXmlRelativePath));
    assertThat(listFilesRecursively(UnpackedAarUtils.getResDir(anotherResourceAarDir)))
        .containsExactly(anotherResourceAarDir.toPath().resolve(colorsXmlRelativePath));
    assertThat(
            new String(
                Files.readAllBytes(new File(resourceAarDir, stringsXmlRelativePath).toPath()),
                UTF_8))
        .isEqualTo(STRINGS_XML_CONTENT);
    assertThat(
            new String(
                Files.readAllBytes(new File(anotherResourceAarDir, colorsXmlRelativePath).toPath()),
                UTF_8))
        .isEqualTo(COLORS_XML_CONTENT);

    String messages = writingOutputSink.getMessages();
    assertThat(messages).contains("Copied 2 AARs");
  }

  @Test
  public void testRefresh_mergeLibraryWithConflictResourceFile_renameConflictResourceFiles()
      throws IOException {
    // aars cached last time but not included in current build output. It should be removed after
    // current sync get finished.
    UnpackedAars unpackedAars = UnpackedAars.getInstance(project);
    File aarCacheDir = unpackedAars.getCacheDir();

    // new aar without jar files
    String resourceAar = "resource.aar";
    String colorsXmlRelativePath = "res/values/colors.xml";
    AarLibraryFileBuilder.aar(workspaceRoot, resourceAar)
        .src(colorsXmlRelativePath, ImmutableList.of(COLORS_XML_CONTENT))
        .build();
    ArtifactLocation resourceAarArtifactLocation = generateArtifactLocation(resourceAar);
    AarLibrary resourceAarLibrary =
        AarLibraryFactory.create(resourceAarArtifactLocation, "com.foo");

    // new aar with jar files
    String anotherResourceAar = "another_resource.aar";
    AarLibraryFileBuilder.aar(workspaceRoot, anotherResourceAar)
        .src(colorsXmlRelativePath, ImmutableList.of(CONFLICT_COLORS_XML_CONTENT))
        .build();
    ArtifactLocation anotherResourceAarArtifactLocation =
        generateArtifactLocation(anotherResourceAar);
    AarLibrary anotherResourceAarLibrary =
        AarLibraryFactory.create(anotherResourceAarArtifactLocation, "com.foo");

    BlazeAndroidImportResult importResult =
        new BlazeAndroidImportResult(
            ImmutableList.of(),
            ImmutableMap.of(
                LibraryKey.libraryNameFromArtifactLocation(resourceAarArtifactLocation),
                resourceAarLibrary,
                LibraryKey.libraryNameFromArtifactLocation(anotherResourceAarArtifactLocation),
                anotherResourceAarLibrary),
            ImmutableList.of(),
            ImmutableList.of());
    BlazeAndroidSyncData syncData =
        new BlazeAndroidSyncData(importResult, new AndroidSdkPlatform("stable", 15));
    BlazeProjectData blazeProjectData =
        MockBlazeProjectDataBuilder.builder(workspaceRoot)
            .setWorkspaceLanguageSettings(
                new WorkspaceLanguageSettings(WorkspaceType.ANDROID, ImmutableSet.of()))
            .setSyncState(new SyncState.Builder().put(syncData).build())
            .setArtifactLocationDecoder(artifactLocationDecoder)
            .build();
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

    assertThat(aarCacheDir.list()).hasLength(3);
    File mergedResourceAarDir = unpackedAars.getAarDir(resourceAarLibrary);
    File mergedAnotherResourceAarDir = unpackedAars.getAarDir(anotherResourceAarLibrary);
    assertThat(mergedResourceAarDir).isEqualTo(mergedAnotherResourceAarDir);
    File resourceAarDir =
        new File(
            unpackedAars.getCacheDir(),
            UnpackedAarUtils.getAarDirName(
                artifactLocationDecoder.resolveOutput(resourceAarLibrary.aarArtifact)));
    File anotherResourceAarDir =
        new File(
            unpackedAars.getCacheDir(),
            UnpackedAarUtils.getAarDirName(
                artifactLocationDecoder.resolveOutput(anotherResourceAarLibrary.aarArtifact)));
    assertThat(aarCacheDir.listFiles())
        .asList()
        .containsExactly(mergedResourceAarDir, resourceAarDir, anotherResourceAarDir);

    // assert two aars get merged correctly
    assertThat(mergedResourceAarDir.list()).asList().containsExactly("res");
    ImmutableList<Path> resourceFiles =
        listFilesRecursively(UnpackedAarUtils.getResDir(mergedResourceAarDir));
    assertThat(resourceFiles).hasSize(2);
    assertThat(
            ImmutableList.of(
                mergedResourceAarDir.toPath().resolve(colorsXmlRelativePath),
                mergedResourceAarDir
                    .toPath()
                    .resolve(
                        "res/values/colors-"
                            + FileUtil.getNameWithoutExtension(resourceAarDir)
                            + ".xml"),
                mergedResourceAarDir
                    .toPath()
                    .resolve(
                        "res/values/colors-"
                            + FileUtil.getNameWithoutExtension(anotherResourceAarDir)
                            + ".xml")))
        .containsAllIn(resourceFiles);
    assertThat(
            resourceFiles.stream()
                .map(
                    file -> {
                      try {
                        return new String(Files.readAllBytes(file), UTF_8);
                      } catch (IOException e) {
                        return "";
                      }
                    })
                .collect(toImmutableList()))
        .containsExactly(COLORS_XML_CONTENT, CONFLICT_COLORS_XML_CONTENT);

    // assert each single aar is unpacked and copied correctly
    assertThat(listFilesRecursively(UnpackedAarUtils.getResDir(resourceAarDir)))
        .containsExactly(resourceAarDir.toPath().resolve(colorsXmlRelativePath));
    assertThat(listFilesRecursively(UnpackedAarUtils.getResDir(anotherResourceAarDir)))
        .containsExactly(anotherResourceAarDir.toPath().resolve(colorsXmlRelativePath));
    assertThat(
            new String(
                Files.readAllBytes(new File(resourceAarDir, colorsXmlRelativePath).toPath()),
                UTF_8))
        .isEqualTo(COLORS_XML_CONTENT);
    assertThat(
            new String(
                Files.readAllBytes(new File(anotherResourceAarDir, colorsXmlRelativePath).toPath()),
                UTF_8))
        .isEqualTo(CONFLICT_COLORS_XML_CONTENT);

    String messages = writingOutputSink.getMessages();
    assertThat(messages).contains("Copied 2 AARs");
  }

  @Test
  public void testRefresh_mergeLibraryWithSameResourceFile_noDuplicateFiles() throws IOException {
    // aars cached last time but not included in current build output. It should be removed after
    // current sync get finished.
    UnpackedAars unpackedAars = UnpackedAars.getInstance(project);
    File aarCacheDir = unpackedAars.getCacheDir();

    // new aar without jar files
    String resourceAar = "resource.aar";
    String colorsXmlRelativePath = "res/values/colors.xml";
    AarLibraryFileBuilder.aar(workspaceRoot, resourceAar)
        .src(colorsXmlRelativePath, ImmutableList.of(COLORS_XML_CONTENT))
        .build();
    ArtifactLocation resourceAarArtifactLocation = generateArtifactLocation(resourceAar);
    AarLibrary resourceAarLibrary =
        AarLibraryFactory.create(resourceAarArtifactLocation, "com.foo");

    // new aar with jar files
    String anotherResourceAar = "another_resource.aar";
    AarLibraryFileBuilder.aar(workspaceRoot, anotherResourceAar)
        .src(colorsXmlRelativePath, ImmutableList.of(COLORS_XML_CONTENT))
        .build();
    ArtifactLocation anotherResourceAarArtifactLocation =
        generateArtifactLocation(anotherResourceAar);
    AarLibrary anotherResourceAarLibrary =
        AarLibraryFactory.create(anotherResourceAarArtifactLocation, "com.foo");

    BlazeAndroidImportResult importResult =
        new BlazeAndroidImportResult(
            ImmutableList.of(),
            ImmutableMap.of(
                LibraryKey.libraryNameFromArtifactLocation(resourceAarArtifactLocation),
                resourceAarLibrary,
                LibraryKey.libraryNameFromArtifactLocation(anotherResourceAarArtifactLocation),
                anotherResourceAarLibrary),
            ImmutableList.of(),
            ImmutableList.of());
    BlazeAndroidSyncData syncData =
        new BlazeAndroidSyncData(importResult, new AndroidSdkPlatform("stable", 15));
    BlazeProjectData blazeProjectData =
        MockBlazeProjectDataBuilder.builder(workspaceRoot)
            .setWorkspaceLanguageSettings(
                new WorkspaceLanguageSettings(WorkspaceType.ANDROID, ImmutableSet.of()))
            .setSyncState(new SyncState.Builder().put(syncData).build())
            .setArtifactLocationDecoder(artifactLocationDecoder)
            .build();
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

    assertThat(aarCacheDir.list()).hasLength(3);
    File mergedResourceAarDir = unpackedAars.getAarDir(resourceAarLibrary);
    File mergedAnotherResourceAarDir = unpackedAars.getAarDir(anotherResourceAarLibrary);
    assertThat(mergedResourceAarDir).isEqualTo(mergedAnotherResourceAarDir);
    File resourceAarDir =
        new File(
            unpackedAars.getCacheDir(),
            UnpackedAarUtils.getAarDirName(
                artifactLocationDecoder.resolveOutput(resourceAarLibrary.aarArtifact)));
    File anotherResourceAarDir =
        new File(
            unpackedAars.getCacheDir(),
            UnpackedAarUtils.getAarDirName(
                artifactLocationDecoder.resolveOutput(anotherResourceAarLibrary.aarArtifact)));
    assertThat(aarCacheDir.listFiles())
        .asList()
        .containsExactly(mergedResourceAarDir, resourceAarDir, anotherResourceAarDir);

    // assert two aars get merged correctly
    assertThat(mergedResourceAarDir.list()).asList().containsExactly("res");
    ImmutableList<Path> resourceFiles =
        listFilesRecursively(UnpackedAarUtils.getResDir(mergedResourceAarDir));
    assertThat(resourceFiles)
        .containsExactly(mergedResourceAarDir.toPath().resolve(colorsXmlRelativePath));
    assertThat(
            new String(
                Files.readAllBytes(new File(mergedResourceAarDir, colorsXmlRelativePath).toPath()),
                UTF_8))
        .isEqualTo(COLORS_XML_CONTENT);

    // assert each single aar is unpacked and copied correctly
    assertThat(listFilesRecursively(UnpackedAarUtils.getResDir(resourceAarDir)))
        .containsExactly(resourceAarDir.toPath().resolve(colorsXmlRelativePath));
    assertThat(listFilesRecursively(UnpackedAarUtils.getResDir(anotherResourceAarDir)))
        .containsExactly(anotherResourceAarDir.toPath().resolve(colorsXmlRelativePath));
    assertThat(
            new String(
                Files.readAllBytes(new File(resourceAarDir, colorsXmlRelativePath).toPath()),
                UTF_8))
        .isEqualTo(COLORS_XML_CONTENT);
    assertThat(
            new String(
                Files.readAllBytes(new File(anotherResourceAarDir, colorsXmlRelativePath).toPath()),
                UTF_8))
        .isEqualTo(COLORS_XML_CONTENT);

    String messages = writingOutputSink.getMessages();
    assertThat(messages).contains("Copied 2 AARs");
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

  private ImmutableList<Path> listFilesRecursively(File dir) throws IOException {
    try (Stream<Path> stream =
        Files.find(
            dir.toPath(), Integer.MAX_VALUE, (filePath, fileAttr) -> fileAttr.isRegularFile())) {
      return stream.collect(toImmutableList());
    }
  }
}
