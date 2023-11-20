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
package com.google.idea.blaze.android.projectsystem;

import static com.google.common.truth.Truth.assertThat;
import static com.google.idea.blaze.android.sync.projectstructure.BlazeAndroidProjectStructureSyncer.moduleNameForAndroidModule;
import static com.google.idea.blaze.base.sync.data.BlazeDataStorage.WORKSPACE_MODULE_NAME;

import com.google.idea.blaze.android.BlazeAndroidIntegrationTestCase;
import com.google.idea.blaze.android.filecache.ArtifactNotFoundException;
import com.google.idea.blaze.android.filecache.CacheEntry;
import com.google.idea.blaze.android.filecache.MockArtifactCache;
import com.google.idea.blaze.android.libraries.RenderJarCache;
import com.google.idea.blaze.android.sync.aspects.strategy.RenderResolveOutputGroupProvider;
import com.google.idea.blaze.android.sync.model.AndroidResourceModule;
import com.google.idea.blaze.android.sync.model.AndroidResourceModuleRegistry;
import com.google.idea.blaze.base.command.buildresult.LocalFileOutputArtifactWithoutDigest;
import com.google.idea.blaze.base.command.buildresult.OutputArtifactWithoutDigest;
import com.google.idea.blaze.base.filecache.FileCache;
import com.google.idea.blaze.base.ideinfo.AndroidIdeInfo;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetKey;
import com.google.idea.blaze.base.ideinfo.TargetMap;
import com.google.idea.blaze.base.ideinfo.TargetMapBuilder;
import com.google.idea.blaze.base.io.FileOperationProvider;
import com.google.idea.blaze.base.model.MockBlazeProjectDataBuilder;
import com.google.idea.blaze.base.model.MockBlazeProjectDataManager;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.settings.BlazeImportSettings;
import com.google.idea.blaze.base.settings.BlazeImportSettingsManager;
import com.google.idea.blaze.base.sync.BlazeSyncPlugin.ModuleEditor;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.base.sync.projectstructure.ModuleEditorProvider;
import com.google.idea.blaze.base.sync.workspace.ArtifactLocationDecoder;
import com.google.idea.blaze.base.sync.workspace.MockArtifactLocationDecoder;
import com.google.idea.common.experiments.ExperimentService;
import com.google.idea.common.experiments.MockExperimentService;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.StdModuleTypes;
import java.io.File;
import java.util.HashMap;
import java.util.Objects;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link RenderJarClassFileFinder} */
@RunWith(JUnit4.class)
public class RenderJarClassFileFinderTest extends BlazeAndroidIntegrationTestCase {

  private static final String BLAZE_BIN = "blaze-out/crosstool/bin";

  // Utility map to quickly access Labels in target map. Populated by `buildTargetMap`, and used to
  // ensure the tests don't accidentally access targets not set up in the target map
  private final HashMap<String, Label> targetNameToLabel = new HashMap<>();

  private ArtifactLocationDecoder artifactLocationDecoder;
  private MockArtifactCache artifactCache;

  @Before
  public void initTest() throws ArtifactNotFoundException {
    TargetMap targetMap = buildTargetMap();
    setTargetMap(targetMap);

    // Since this is not a light test, the ArtifactLocationDecoder points to the actual file in the
    // File System
    artifactLocationDecoder =
        new MockArtifactLocationDecoder() {
          @Override
          public File decode(ArtifactLocation artifactLocation) {
            File f =
                new File(fileSystem.getRootDir(), artifactLocation.getExecutionRootRelativePath());

            // Create the artifact if it does not exist
            // This allows us to set fileModifiedTime for the file which is used by RenderJarCache
            FileOperationProvider.getInstance().mkdirs(f);

            // Set last modified time for RenderJarCache to use
            FileOperationProvider.getInstance().setFileModifiedTime(f, 1000L);
            return f;
          }
        };

    registerProjectService(
        BlazeProjectDataManager.class,
        new MockBlazeProjectDataManager(
            MockBlazeProjectDataBuilder.builder(workspaceRoot)
                .setTargetMap(targetMap)
                .setArtifactLocationDecoder(artifactLocationDecoder)
                .build()));

    setProjectView(
        "targets:",
        "  //com/google/example/simple/bin_a:bin_a",
        "  //com/google/example/simple/bin_b:bin_b",
        "  //com/google/example/simple/bin_c:bin_c");

    MockExperimentService experimentService = new MockExperimentService();
    registerApplicationComponent(ExperimentService.class, experimentService);
    experimentService.setExperiment(RenderResolveOutputGroupProvider.buildOnSync, true);
    // Disable resource resolution from Render Jars
    experimentService.setExperiment(RenderJarClassFileFinder.resolveResourceClasses, false);

    ApplicationManager.getApplication().runWriteAction(this::createAndRegisterModules);

    artifactCache = new MockArtifactCache();
    registerExtension(FileCache.EP_NAME, new RenderJarCache.FileCacheAdapter());
    registerProjectService(
        RenderJarCache.class,
        new RenderJarCache(
            getProject(), RenderJarCache.getCacheDirForProject(getProject()), artifactCache));

    createBinaryJars();
    FileCache.EP_NAME.extensions().forEach(ep -> ep.initialize(getProject()));
  }

  /** Tests that .workspace module can find classes from all binaries in the projectview. */
  @Test
  public void workspaceModule_canFindAllClassesInAllBinaries() throws Exception {
    Module workspaceModule =
        ModuleManager.getInstance(getProject()).findModuleByName(WORKSPACE_MODULE_NAME);
    assertThat(workspaceModule).isNotNull();

    RenderJarClassFileFinder classFileFinder = new RenderJarClassFileFinder(workspaceModule);

    File cacheDir = RenderJarCache.getInstance(getProject()).getCacheDir();

    CacheEntry binACacheEntry =
        CacheEntry.forArtifact(
            (LocalFileOutputArtifactWithoutDigest)
                artifactLocationDecoder.resolveOutput(
                    getArtifactLocation("com/google/example/simple/bin_a.jar")));
    String binAJar = cacheDir.getAbsoluteFile() + "/" + binACacheEntry.getFileName();

    CacheEntry binBCacheEntry =
        CacheEntry.forArtifact(
            (LocalFileOutputArtifactWithoutDigest)
                artifactLocationDecoder.resolveOutput(
                    getArtifactLocation("com/google/example/simple/bin_b.jar")));
    String binBJar = cacheDir.getAbsoluteFile() + "/" + binBCacheEntry.getFileName();

    CacheEntry binCCacheEntry =
        CacheEntry.forArtifact(
            (LocalFileOutputArtifactWithoutDigest)
                artifactLocationDecoder.resolveOutput(
                    getArtifactLocation("com/google/example/simple/bin_c.jar")));
    String binCJar = cacheDir.getAbsoluteFile() + "/" + binCCacheEntry.getFileName();

    assertThat(
            Objects.requireNonNull(
                    classFileFinder.findClassFile("com.google.example.simple.src_a.SrcA"))
                .getContent())
        .isEqualTo(
            Objects.requireNonNull(
                    fileSystem.findFile(binAJar + "!/com/google/example/simple/src_a/SrcA.class"))
                .contentsToByteArray());
    assertThat(
            Objects.requireNonNull(
                    classFileFinder.findClassFile("com.google.example.simple.src_a.SrcA$Inner"))
                .getContent())
        .isEqualTo(
            Objects.requireNonNull(
                    fileSystem.findFile(
                        binAJar + "!/com/google/example/simple/src_a/SrcA$Inner.class"))
                .contentsToByteArray());

    assertThat(
            Objects.requireNonNull(
                    classFileFinder.findClassFile("com.google.example.simple.src_b.SrcB"))
                .getContent())
        .isEqualTo(
            Objects.requireNonNull(
                    fileSystem.findFile(binBJar + "!/com/google/example/simple/src_b/SrcB.class"))
                .contentsToByteArray());
    assertThat(
            Objects.requireNonNull(
                    classFileFinder.findClassFile("com.google.example.simple.src_b.SrcB$Inner"))
                .getContent())
        .isEqualTo(
            Objects.requireNonNull(
                    fileSystem.findFile(
                        binBJar + "!/com/google/example/simple/src_b/SrcB$Inner.class"))
                .contentsToByteArray());

    assertThat(
            Objects.requireNonNull(
                    classFileFinder.findClassFile("com.google.example.simple.src_c.SrcC"))
                .getContent())
        .isEqualTo(
            Objects.requireNonNull(
                    fileSystem.findFile(binCJar + "!/com/google/example/simple/src_c/SrcC.class"))
                .contentsToByteArray());
    assertThat(
            Objects.requireNonNull(
                    classFileFinder.findClassFile("com.google.example.simple.src_c.SrcC$Inner"))
                .getContent())
        .isEqualTo(
            Objects.requireNonNull(
                    fileSystem.findFile(
                        binCJar + "!/com/google/example/simple/src_c/SrcC$Inner.class"))
                .contentsToByteArray());

    assertThat(
            Objects.requireNonNull(
                    classFileFinder.findClassFile(
                        "com.google.example.simple.trans_dep_a.TransDepA"))
                .getContent())
        .isEqualTo(
            Objects.requireNonNull(
                    fileSystem.findFile(
                        binAJar + "!/com/google/example/simple/trans_dep_a/TransDepA.class"))
                .contentsToByteArray());
    assertThat(
            Objects.requireNonNull(
                    classFileFinder.findClassFile(
                        "com.google.example.simple.trans_dep_a.TransDepA$Inner"))
                .getContent())
        .isEqualTo(
            Objects.requireNonNull(
                    fileSystem.findFile(
                        binAJar + "!/com/google/example/simple/trans_dep_a/TransDepA$Inner.class"))
                .contentsToByteArray());

    assertThat(
            Objects.requireNonNull(
                    classFileFinder.findClassFile(
                        "com.google.example.simple.trans_dep_b.TransDepB"))
                .getContent())
        .isEqualTo(
            Objects.requireNonNull(
                    fileSystem.findFile(
                        binBJar + "!/com/google/example/simple/trans_dep_b/TransDepB.class"))
                .contentsToByteArray());
    assertThat(
            Objects.requireNonNull(
                    classFileFinder.findClassFile(
                        "com.google.example.simple.trans_dep_b.TransDepB$Inner"))
                .getContent())
        .isEqualTo(
            Objects.requireNonNull(
                    fileSystem.findFile(
                        binBJar + "!/com/google/example/simple/trans_dep_b/TransDepB$Inner.class"))
                .contentsToByteArray());

    assertThat(
            Objects.requireNonNull(
                    classFileFinder.findClassFile(
                        "com.google.example.simple.trans_dep_c.TransDepC"))
                .getContent())
        .isEqualTo(
            Objects.requireNonNull(
                    fileSystem.findFile(
                        binCJar + "!/com/google/example/simple/trans_dep_c/TransDepC.class"))
                .contentsToByteArray());
    assertThat(
            Objects.requireNonNull(
                    classFileFinder.findClassFile(
                        "com.google.example.simple.trans_dep_c.TransDepC$Inner"))
                .getContent())
        .isEqualTo(
            Objects.requireNonNull(
                    fileSystem.findFile(
                        binCJar + "!/com/google/example/simple/trans_dep_c/TransDepC$Inner.class"))
                .contentsToByteArray());
  }

  /**
   * Tests that resource modules can correctly find classes corresponding to sources of the targets
   * that comprise the resource module.
   */
  @Test
  public void resourceModule_canFindSourceClasses() throws Exception {
    AndroidResourceModuleRegistry moduleRegistry =
        AndroidResourceModuleRegistry.getInstance(getProject());
    Module aResourceModule =
        moduleRegistry.getModuleContainingResourcesOf(getTargetKey("/src_a:src_a"));
    RenderJarClassFileFinder aClassFileFinder = new RenderJarClassFileFinder(aResourceModule);

    File cacheDir = RenderJarCache.getInstance(getProject()).getCacheDir();

    CacheEntry binACacheEntry =
        CacheEntry.forArtifact(
            (LocalFileOutputArtifactWithoutDigest)
                artifactLocationDecoder.resolveOutput(
                    getArtifactLocation("com/google/example/simple/bin_a.jar")));
    String binAJar = cacheDir.getAbsoluteFile() + "/" + binACacheEntry.getFileName();

    CacheEntry binBCacheEntry =
        CacheEntry.forArtifact(
            (LocalFileOutputArtifactWithoutDigest)
                artifactLocationDecoder.resolveOutput(
                    getArtifactLocation("com/google/example/simple/bin_b.jar")));
    String binBJar = cacheDir.getAbsoluteFile() + "/" + binBCacheEntry.getFileName();

    CacheEntry binCCacheEntry =
        CacheEntry.forArtifact(
            (LocalFileOutputArtifactWithoutDigest)
                artifactLocationDecoder.resolveOutput(
                    getArtifactLocation("com/google/example/simple/bin_c.jar")));
    String binCJar = cacheDir.getAbsoluteFile() + "/" + binCCacheEntry.getFileName();

    assertThat(
            Objects.requireNonNull(
                    aClassFileFinder.findClassFile("com.google.example.simple.src_a.SrcA"))
                .getContent())
        .isEqualTo(
            Objects.requireNonNull(
                    fileSystem.findFile(binAJar + "!/com/google/example/simple/src_a/SrcA.class"))
                .contentsToByteArray());
    assertThat(
            Objects.requireNonNull(
                    aClassFileFinder.findClassFile("com.google.example.simple.src_a.SrcA$Inner"))
                .getContent())
        .isEqualTo(
            Objects.requireNonNull(
                    fileSystem.findFile(
                        binAJar + "!/com/google/example/simple/src_a/SrcA$Inner.class"))
                .contentsToByteArray());

    assertThat(
            Objects.requireNonNull(
                    aClassFileFinder.findClassFile("com.google.example.simple.src_b.SrcB"))
                .getContent())
        .isEqualTo(
            Objects.requireNonNull(
                    fileSystem.findFile(binBJar + "!/com/google/example/simple/src_b/SrcB.class"))
                .contentsToByteArray());
    assertThat(
            Objects.requireNonNull(
                    aClassFileFinder.findClassFile("com.google.example.simple.src_b.SrcB$Inner"))
                .getContent())
        .isEqualTo(
            Objects.requireNonNull(
                    fileSystem.findFile(
                        binBJar + "!/com/google/example/simple/src_b/SrcB$Inner.class"))
                .contentsToByteArray());

    Module cResourceModule =
        moduleRegistry.getModuleContainingResourcesOf(getTargetKey("/src_c:src_c"));
    RenderJarClassFileFinder cClassFileFinder = new RenderJarClassFileFinder(cResourceModule);
    assertThat(
            Objects.requireNonNull(
                    cClassFileFinder.findClassFile("com.google.example.simple.src_c.SrcC"))
                .getContent())
        .isEqualTo(
            Objects.requireNonNull(
                    fileSystem.findFile(binCJar + "!/com/google/example/simple/src_c/SrcC.class"))
                .contentsToByteArray());
    assertThat(
            Objects.requireNonNull(
                    cClassFileFinder.findClassFile("com.google.example.simple.src_c.SrcC$Inner"))
                .getContent())
        .isEqualTo(
            Objects.requireNonNull(
                    fileSystem.findFile(
                        binCJar + "!/com/google/example/simple/src_c/SrcC$Inner.class"))
                .contentsToByteArray());
  }

  /**
   * Tests that resource modules can find classes from dependencies of source targets that comprise
   * the resource module.
   */
  @Test
  public void resourceModule_canFindDependencyClasses() throws Exception {
    AndroidResourceModuleRegistry moduleRegistry =
        AndroidResourceModuleRegistry.getInstance(getProject());
    Module aResourceModule =
        moduleRegistry.getModuleContainingResourcesOf(getTargetKey("/src_a:src_a"));
    RenderJarClassFileFinder aClassFileFinder = new RenderJarClassFileFinder(aResourceModule);

    File cacheDir = RenderJarCache.getInstance(getProject()).getCacheDir();

    CacheEntry binACacheEntry =
        CacheEntry.forArtifact(
            (LocalFileOutputArtifactWithoutDigest)
                artifactLocationDecoder.resolveOutput(
                    getArtifactLocation("com/google/example/simple/bin_a.jar")));
    String binAJar = cacheDir.getAbsoluteFile() + "/" + binACacheEntry.getFileName();

    CacheEntry binBCacheEntry =
        CacheEntry.forArtifact(
            (LocalFileOutputArtifactWithoutDigest)
                artifactLocationDecoder.resolveOutput(
                    getArtifactLocation("com/google/example/simple/bin_b.jar")));
    String binBJar = cacheDir.getAbsoluteFile() + "/" + binBCacheEntry.getFileName();

    CacheEntry binCCacheEntry =
        CacheEntry.forArtifact(
            (LocalFileOutputArtifactWithoutDigest)
                artifactLocationDecoder.resolveOutput(
                    getArtifactLocation("com/google/example/simple/bin_c.jar")));
    String binCJar = cacheDir.getAbsoluteFile() + "/" + binCCacheEntry.getFileName();

    assertThat(
            Objects.requireNonNull(
                    aClassFileFinder.findClassFile(
                        "com.google.example.simple.trans_dep_a.TransDepA"))
                .getContent())
        .isEqualTo(
            Objects.requireNonNull(
                    fileSystem.findFile(
                        binAJar + "!/com/google/example/simple/trans_dep_a/TransDepA.class"))
                .contentsToByteArray());
    assertThat(
            Objects.requireNonNull(
                    aClassFileFinder.findClassFile(
                        "com.google.example.simple.trans_dep_a.TransDepA$Inner"))
                .getContent())
        .isEqualTo(
            Objects.requireNonNull(
                    fileSystem.findFile(
                        binAJar + "!/com/google/example/simple/trans_dep_a/TransDepA$Inner.class"))
                .contentsToByteArray());

    assertThat(
            Objects.requireNonNull(
                    aClassFileFinder.findClassFile(
                        "com.google.example.simple.trans_dep_b.TransDepB"))
                .getContent())
        .isEqualTo(
            Objects.requireNonNull(
                    fileSystem.findFile(
                        binBJar + "!/com/google/example/simple/trans_dep_b/TransDepB.class"))
                .contentsToByteArray());
    assertThat(
            Objects.requireNonNull(
                    aClassFileFinder.findClassFile(
                        "com.google.example.simple.trans_dep_b.TransDepB$Inner"))
                .getContent())
        .isEqualTo(
            Objects.requireNonNull(
                    fileSystem.findFile(
                        binBJar + "!/com/google/example/simple/trans_dep_b/TransDepB$Inner.class"))
                .contentsToByteArray());

    Module cResourceModule =
        moduleRegistry.getModuleContainingResourcesOf(getTargetKey("/src_c:src_c"));
    RenderJarClassFileFinder cClassFileFinder = new RenderJarClassFileFinder(cResourceModule);
    assertThat(
            Objects.requireNonNull(
                    cClassFileFinder.findClassFile(
                        "com.google.example.simple.trans_dep_c.TransDepC"))
                .getContent())
        .isEqualTo(
            Objects.requireNonNull(
                    fileSystem.findFile(
                        binCJar + "!/com/google/example/simple/trans_dep_c/TransDepC.class"))
                .contentsToByteArray());
    assertThat(
            Objects.requireNonNull(
                    cClassFileFinder.findClassFile(
                        "com.google.example.simple.trans_dep_c.TransDepC$Inner"))
                .getContent())
        .isEqualTo(
            Objects.requireNonNull(
                    fileSystem.findFile(
                        binCJar + "!/com/google/example/simple/trans_dep_c/TransDepC$Inner.class"))
                .contentsToByteArray());
  }

  @Test
  public void failsWhenResolvingResourcesPresentInRenderJars() {
    Module workspaceModule =
        ModuleManager.getInstance(getProject()).findModuleByName(WORKSPACE_MODULE_NAME);
    assertThat(workspaceModule).isNotNull();

    RenderJarClassFileFinder classFileFinder = new RenderJarClassFileFinder(workspaceModule);

    assertThat(classFileFinder.findClassFile("com.google.example.simple.bin_a.R$color")).isNull();
    assertThat(classFileFinder.findClassFile("com.google.example.simple.src_b.R$attr")).isNull();
    assertThat(classFileFinder.findClassFile("com.google.example.simple.trans_dep_c.R$dimen"))
        .isNull();
  }

  /**
   * Creates the .workspace module and resource modules, and registers the resource modules in
   * {@link AndroidResourceModuleRegistry}.
   */
  private void createAndRegisterModules() {
    ApplicationManager.getApplication().assertWriteAccessAllowed();

    AndroidResourceModuleRegistry moduleRegistry =
        AndroidResourceModuleRegistry.getInstance(getProject());

    BlazeImportSettings importSettings =
        BlazeImportSettingsManager.getInstance(getProject()).getImportSettings();
    ModuleEditor moduleEditor =
        ModuleEditorProvider.getInstance().getModuleEditor(getProject(), importSettings);

    moduleEditor.createModule(WORKSPACE_MODULE_NAME, StdModuleTypes.JAVA);

    AndroidResourceModule resourceModule1 =
        AndroidResourceModule.builder(getTargetKey("/src_a:src_a"))
            .addSourceTarget(getTargetKey("/src_b:src_b"))
            .build();

    AndroidResourceModule resourceModule2 =
        AndroidResourceModule.builder(getTargetKey("/src_c:src_c")).build();

    Module module1 =
        moduleEditor.createModule(
            moduleNameForAndroidModule(resourceModule1.targetKey), StdModuleTypes.JAVA);
    Module module2 =
        moduleEditor.createModule(
            moduleNameForAndroidModule(resourceModule2.targetKey), StdModuleTypes.JAVA);
    moduleRegistry.put(module1, resourceModule1);
    moduleRegistry.put(module2, resourceModule2);
    moduleEditor.commit();
  }

  /**
   * Creates a target map with the following dependency structure:
   *
   * <pre>{@code
   * bin_a -> src_a
   * bin_b -> src_b
   * bin_c -> src_c
   *
   * src_a -> trans_dep_a
   * src_b -> trans_dep_b
   * src_c -> trans_dep_c
   *
   * NOTE: x -> y means x is directly dependent on y
   * }</pre>
   */
  private TargetMap buildTargetMap() {
    Label binA = createAndTrackLabel("/bin_a:bin_a");
    Label binB = createAndTrackLabel("/bin_b:bin_b");
    Label binC = createAndTrackLabel("/bin_c:bin_c");

    Label directDepA = createAndTrackLabel("/src_a:src_a");
    Label directDepB = createAndTrackLabel("/src_b:src_b");
    Label directDepC = createAndTrackLabel("/src_c:src_c");

    Label transDepA = createAndTrackLabel("/trans_dep_a:trans_dep_a");
    Label transDepB = createAndTrackLabel("/trans_dep_b:trans_dep_b");
    Label transDepC = createAndTrackLabel("/trans_dep_c:trans_dep_c");

    return TargetMapBuilder.builder()
        .addTarget(
            mockBinaryTargetIdeInfoBuilder("com/google/example/simple/bin_a.jar")
                .setLabel(binA)
                .addDependency(directDepA))
        .addTarget(
            mockBinaryTargetIdeInfoBuilder("com/google/example/simple/bin_b.jar")
                .setLabel(binB)
                .addDependency(directDepB))
        .addTarget(
            mockBinaryTargetIdeInfoBuilder("com/google/example/simple/bin_c.jar")
                .setLabel(binC)
                .addDependency(directDepC))
        .addTarget(mockLibraryTargetIdeInfoBuilder().setLabel(directDepA).addDependency(transDepA))
        .addTarget(mockLibraryTargetIdeInfoBuilder().setLabel(directDepB).addDependency(transDepB))
        .addTarget(mockLibraryTargetIdeInfoBuilder().setLabel(directDepC).addDependency(transDepC))
        .addTarget(mockLibraryTargetIdeInfoBuilder().setLabel(transDepA))
        .addTarget(mockLibraryTargetIdeInfoBuilder().setLabel(transDepB))
        .addTarget(mockLibraryTargetIdeInfoBuilder().setLabel(transDepC))
        .build();
  }

  /**
   * Creates empty files corresponding to content entries in a JAR. Doesn't create an actual
   * archive, only mimics the archive roots in file system.
   */
  private void createBinaryJars() throws ArtifactNotFoundException {
    File cacheDirFile = RenderJarCache.getInstance(getProject()).getCacheDir();
    fileSystem.createDirectory(cacheDirFile.getAbsolutePath());
    String cacheDir = cacheDirFile.getPath();

    OutputArtifactWithoutDigest binAArtifact =
        (OutputArtifactWithoutDigest)
            artifactLocationDecoder.resolveOutput(
                getArtifactLocation("com/google/example/simple/bin_a.jar"));
    CacheEntry binACacheEntry = CacheEntry.forArtifact(binAArtifact);
    String binAJar = cacheDir + "/" + binACacheEntry.getFileName();
    fileSystem.createFile(binAJar);
    fileSystem.createFile(binAJar + "!/com/google/example/simple/bin_a/MainActivity.class");
    fileSystem.createFile(binAJar + "!/com/google/example/simple/bin_a/R$color.class");
    fileSystem.createFile(binAJar + "!/com/google/example/simple/src_a/SrcA.class");
    fileSystem.createFile(binAJar + "!/com/google/example/simple/src_a/SrcA$Inner.class");
    fileSystem.createFile(binAJar + "!/com/google/example/simple/src_a/R$attr.class");
    fileSystem.createFile(binAJar + "!/com/google/example/simple/trans_dep_a/TransDepA.class");
    fileSystem.createFile(
        binAJar + "!/com/google/example/simple/trans_dep_a/TransDepA$Inner.class");
    fileSystem.createFile(binAJar + "!/com/google/example/simple/trans_dep_a/R$dimen.class");
    artifactCache.addTrackedFile(binAArtifact, binAJar);

    OutputArtifactWithoutDigest binBArtifact =
        (OutputArtifactWithoutDigest)
            artifactLocationDecoder.resolveOutput(
                getArtifactLocation("com/google/example/simple/bin_b.jar"));
    CacheEntry binBCacheEntry = CacheEntry.forArtifact(binBArtifact);
    String binBJar = cacheDir + "/" + binBCacheEntry.getFileName();
    fileSystem.createFile(binBJar);
    fileSystem.createFile(binBJar + "!/com/google/example/simple/bin_b/MainActivity.class");
    fileSystem.createFile(binBJar + "!/com/google/example/simple/bin_b/R$color.class");
    fileSystem.createFile(binBJar + "!/com/google/example/simple/src_b/SrcB.class");
    fileSystem.createFile(binBJar + "!/com/google/example/simple/src_b/SrcB$Inner.class");
    fileSystem.createFile(binBJar + "!/com/google/example/simple/src_b/R$attr.class");
    fileSystem.createFile(binBJar + "!/com/google/example/simple/trans_dep_b/TransDepB.class");
    fileSystem.createFile(
        binBJar + "!/com/google/example/simple/trans_dep_b/TransDepB$Inner.class");
    fileSystem.createFile(binBJar + "!/com/google/example/simple/trans_dep_b/R$dimen.class");
    artifactCache.addTrackedFile(binBArtifact, binBJar);

    OutputArtifactWithoutDigest binCArtifact =
        (OutputArtifactWithoutDigest)
            artifactLocationDecoder.resolveOutput(
                getArtifactLocation("com/google/example/simple/bin_c.jar"));
    CacheEntry binCCacheEntry = CacheEntry.forArtifact(binCArtifact);
    String binCJar = cacheDir + "/" + binCCacheEntry.getFileName();
    fileSystem.createFile(binCJar);
    fileSystem.createFile(binCJar + "!/com/google/example/simple/bin_c/MainActivity.class");
    fileSystem.createFile(binCJar + "!/com/google/example/simple/bin_c/R$color.class");
    fileSystem.createFile(binCJar + "!/com/google/example/simple/src_c/SrcC.class");
    fileSystem.createFile(binCJar + "!/com/google/example/simple/src_c/SrcC$Inner.class");
    fileSystem.createFile(binCJar + "!/com/google/example/simple/src_c/R$attr.class");
    fileSystem.createFile(binCJar + "!/com/google/example/simple/trans_dep_c/TransDepC.class");
    fileSystem.createFile(
        binCJar + "!/com/google/example/simple/trans_dep_c/TransDepC$Inner.class");
    fileSystem.createFile(binCJar + "!/com/google/example/simple/trans_dep_c/R$dimen.class");
    artifactCache.addTrackedFile(binCArtifact, binCJar);
  }

  /**
   * Creates a {@link Label} from the given {@code targetName} and caches it in {@link
   * #targetNameToLabel}.
   */
  private Label createAndTrackLabel(String targetName) {
    Label label = Label.create("//com/google/example/simple" + targetName);
    targetNameToLabel.put(targetName, label);
    return label;
  }

  /**
   * Returns the {@link TargetKey} of the Label corresponding to {@code targetName}. This method is
   * used to ensure the tests don't accidentally create and test a label not in target map.
   */
  private TargetKey getTargetKey(String targetName) {
    Label label =
        Objects.requireNonNull(
            targetNameToLabel.get(targetName),
            String.format("%s not registered in target map.", targetName));
    return TargetKey.forPlainTarget(label);
  }

  private static TargetIdeInfo.Builder mockLibraryTargetIdeInfoBuilder() {
    return TargetIdeInfo.builder()
        .setKind("android_library")
        .setAndroidInfo(AndroidIdeInfo.builder());
  }

  /**
   * Returns {@link TargetIdeInfo.Builder} which has its "kind" set to "android_binary", and sets
   * AndroidIdeInfo with render JAR field as provided in {@code renderResolveJarRelativePath}.
   */
  private static TargetIdeInfo.Builder mockBinaryTargetIdeInfoBuilder(
      String renderResolveJarRelativePath) {
    return TargetIdeInfo.builder()
        .setKind("android_binary")
        .setAndroidInfo(
            AndroidIdeInfo.builder()
                .setRenderResolveJar(getArtifactLocation(renderResolveJarRelativePath)));
  }

  private static ArtifactLocation getArtifactLocation(String relativePath) {
    return ArtifactLocation.builder()
        .setRootExecutionPathFragment(BLAZE_BIN)
        .setRelativePath(relativePath)
        .build();
  }
}
