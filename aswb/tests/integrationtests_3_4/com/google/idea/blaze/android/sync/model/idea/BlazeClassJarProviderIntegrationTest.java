/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.android.sync.model.idea;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;

import com.android.tools.idea.model.ClassJarProvider;
import com.android.tools.idea.projectsystem.ClassFileFinder;
import com.android.tools.idea.res.ResourceClassRegistry;
import com.android.tools.idea.res.ResourceRepositoryManager;
import com.google.idea.blaze.android.AndroidIntegrationTestSetupRule;
import com.google.idea.blaze.android.projectsystem.BlazeClassFileFinderFactory;
import com.google.idea.blaze.android.projectsystem.TransitiveClosureClassFileFinder;
import com.google.idea.blaze.android.sync.model.AndroidResourceModule;
import com.google.idea.blaze.android.sync.model.AndroidResourceModuleRegistry;
import com.google.idea.blaze.base.BlazeIntegrationTestCase;
import com.google.idea.blaze.base.ideinfo.AndroidIdeInfo;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.ideinfo.JavaIdeInfo;
import com.google.idea.blaze.base.ideinfo.LibraryArtifact;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetKey;
import com.google.idea.blaze.base.ideinfo.TargetMap;
import com.google.idea.blaze.base.ideinfo.TargetMapBuilder;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.MockBlazeProjectDataBuilder;
import com.google.idea.blaze.base.model.MockBlazeProjectDataManager;
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.base.sync.workspace.ArtifactLocationDecoder;
import com.google.idea.common.experiments.ExperimentService;
import com.google.idea.common.experiments.MockExperimentService;
import com.intellij.facet.FacetManager;
import com.intellij.facet.ModifiableFacetModel;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import java.io.File;
import java.io.IOException;
import java.util.List;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.AndroidFacetConfiguration;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Integration test for {@link BlazeClassJarProvider} and {@link TransitiveClosureClassFileFinder}.
 *
 * <p>TODO(b/71713776): Split up these tests once the new project system replacement for the
 * ClassJarProvider API is finalized.
 */
@RunWith(JUnit4.class)
public class BlazeClassJarProviderIntegrationTest extends BlazeIntegrationTestCase {
  private static final String BLAZE_BIN = "blaze-out/crosstool/bin";

  @Rule
  public final AndroidIntegrationTestSetupRule androidSetupRule =
      new AndroidIntegrationTestSetupRule();

  private Module module;
  private ClassJarProvider classJarProvider;
  private ClassFileFinder classFileFinder;

  @Before
  public void doSetup() {
    module = testFixture.getModule();

    ArtifactLocationDecoder decoder =
        (location) -> new File("/src", location.getExecutionRootRelativePath());

    BlazeProjectData blazeProjectData =
        MockBlazeProjectDataBuilder.builder(workspaceRoot)
            .setTargetMap(buildTargetMap())
            .setArtifactLocationDecoder(decoder)
            .build();
    registerProjectService(
        BlazeProjectDataManager.class, new MockBlazeProjectDataManager(blazeProjectData));
    classJarProvider = new BlazeClassJarProvider(getProject());

    MockExperimentService experimentService = new MockExperimentService();
    registerApplicationComponent(ExperimentService.class, experimentService);
    experimentService.setExperimentString(
        BlazeClassFileFinderFactory.CLASS_FILE_FINDER_NAME, "TransitiveClosureClassFileFinder");
    classFileFinder = BlazeClassFileFinderFactory.createBlazeClassFileFinder(module);
  }

  @Test
  public void testFindModuleClassFile() {
    createClassesInJars();

    // Make sure we can find classes in the main resource module.
    VirtualFile found = classFileFinder.findClassFile("com.google.example.main.MainActivity");
    assertThat(found).isNotNull();
    assertThat(found)
        .isEqualTo(
            fileSystem.findFile(
                BLAZE_BIN
                    + "/com/google/example/libmain.jar!"
                    + "/com/google/example/main/MainActivity.class"));
    found = classFileFinder.findClassFile("com.google.example.main.R");
    assertThat(found).isNotNull();
    assertThat(found)
        .isEqualTo(
            fileSystem.findFile(
                BLAZE_BIN
                    + "/com/google/example/main_resources.jar!"
                    + "/com/google/example/main/R.class"));

    found = classFileFinder.findClassFile("com.google.example.main.R$string");
    assertThat(found).isNotNull();
    assertThat(found)
        .isEqualTo(
            fileSystem.findFile(
                BLAZE_BIN
                    + "/com/google/example/main_resources.jar!"
                    + "/com/google/example/main/R$string.class"));

    // And dependencies.
    found = classFileFinder.findClassFile("com.google.example.java.Java");
    assertThat(found).isNotNull();
    assertThat(found)
        .isEqualTo(
            fileSystem.findFile(
                BLAZE_BIN
                    + "/com/google/example/libjava.jar!"
                    + "/com/google/example/java/Java.class"));
    found = classFileFinder.findClassFile("com.google.example.shared.Shared");
    assertThat(found).isNotNull();
    assertThat(found)
        .isEqualTo(
            fileSystem.findFile(
                BLAZE_BIN
                    + "/com/google/example/libshared.jar!"
                    + "/com/google/example/shared/Shared.class"));
    found = classFileFinder.findClassFile("com.google.example.shared2.Shared2");
    assertThat(found).isNotNull();
    assertThat(found)
        .isEqualTo(
            fileSystem.findFile(
                BLAZE_BIN
                    + "/com/google/example/libshared2.jar!"
                    + "/com/google/example/shared2/Shared2.class"));
    found = classFileFinder.findClassFile("com.google.example.transitive.Transitive");
    assertThat(found).isNotNull();
    assertThat(found)
        .isEqualTo(
            fileSystem.findFile(
                BLAZE_BIN
                    + "/com/google/example/libtransitive.jar!"
                    + "/com/google/example/transitive/Transitive.class"));

    // But not resource classes in dependencies.
    assertThat(classFileFinder.findClassFile("com.google.example.resource.R")).isNull();
    assertThat(classFileFinder.findClassFile("com.google.example.resource.R$style")).isNull();
    assertThat(classFileFinder.findClassFile("com.google.example.resource.R$layout")).isNull();
    assertThat(classFileFinder.findClassFile("com.google.example.resource2.R")).isNull();

    // And not classes that are missing.
    assertThat(classFileFinder.findClassFile("com.google.example.main.MissingClass")).isNull();
    assertThat(classFileFinder.findClassFile("com.google.example.main.R$missing")).isNull();

    // And not imported libraries.
    assertThat(classFileFinder.findClassFile("com.google.example.import.Import")).isNull();
    assertThat(
            classFileFinder.findClassFile("com.google.example.transitive.import.TransitiveImport"))
        .isNull();

    // And not unrelated libraries.
    assertThat(classFileFinder.findClassFile("com.google.unrelated.Java")).isNull();
    assertThat(classFileFinder.findClassFile("com.google.unrelated.Android")).isNull();
    assertThat(classFileFinder.findClassFile("com.google.unrelated.Resource")).isNull();
    assertThat(classFileFinder.findClassFile("com.google.unrelated.R")).isNull();
  }

  @Test
  public void testMissingClassJars() {
    createClassesInJars();

    ApplicationManager.getApplication()
        .runWriteAction(
            () -> {
              try {
                // Let's pretend that these haven't been built yet.
                fileSystem.findFile(BLAZE_BIN + "/com/google/example/libmain.jar").delete(this);
                fileSystem.findFile(BLAZE_BIN + "/com/google/example/libjava.jar").delete(this);
                fileSystem.findFile(BLAZE_BIN + "/com/google/example/libshared.jar").delete(this);
                fileSystem
                    .findFile(BLAZE_BIN + "/com/google/example/libtransitive.jar")
                    .delete(this);
              } catch (IOException ignored) {
                // ignored
              }
            });
    // These hasn't been built yet, and shouldn't be found.
    assertThat(classFileFinder.findClassFile("com.google.example.main.MainActivity")).isNull();
    assertThat(classFileFinder.findClassFile("com.google.example.java.Java")).isNull();
    assertThat(classFileFinder.findClassFile("com.google.example.shared.Shared")).isNull();
    assertThat(classFileFinder.findClassFile("com.google.example.transitive.Transitive")).isNull();

    // But these should still be found.
    VirtualFile found = classFileFinder.findClassFile("com.google.example.main.R");
    assertThat(found).isNotNull();
    assertThat(found)
        .isEqualTo(
            fileSystem.findFile(
                BLAZE_BIN
                    + "/com/google/example/main_resources.jar!"
                    + "/com/google/example/main/R.class"));
    found = classFileFinder.findClassFile("com.google.example.main.R$string");
    assertThat(found).isNotNull();
    assertThat(found)
        .isEqualTo(
            fileSystem.findFile(
                BLAZE_BIN
                    + "/com/google/example/main_resources.jar!"
                    + "/com/google/example/main/R$string.class"));
    found = classFileFinder.findClassFile("com.google.example.android.Android");
    assertThat(found).isNotNull();
    assertThat(found)
        .isEqualTo(
            fileSystem.findFile(
                BLAZE_BIN
                    + "/com/google/example/libandroid.jar!"
                    + "/com/google/example/android/Android.class"));
    found = classFileFinder.findClassFile("com.google.example.shared2.Shared2");
    assertThat(found).isNotNull();
    assertThat(found)
        .isEqualTo(
            fileSystem.findFile(
                BLAZE_BIN
                    + "/com/google/example/libshared2.jar!"
                    + "/com/google/example/shared2/Shared2.class"));
  }

  @Test
  public void testGetModuleExternalLibraries() {
    // Need AndroidFact for LocalResourceRepository.
    ApplicationManager.getApplication()
        .runWriteAction(
            () -> {
              ModifiableFacetModel model = FacetManager.getInstance(module).createModifiableModel();
              model.addFacet(new MockAndroidFacet(module));
              model.commit();
            });

    List<File> externalLibraries = classJarProvider.getModuleExternalLibraries(module);
    assertThat(externalLibraries)
        .containsExactly(
            VfsUtilCore.virtualToIoFile(fileSystem.findFile("com/google/example/libimport.jar")),
            VfsUtilCore.virtualToIoFile(
                fileSystem.findFile("com/google/example/transitive/libimport.jar")),
            VfsUtilCore.virtualToIoFile(
                fileSystem.findFile("com/google/example/transitive/libimport2.jar")));

    // Make sure we can generate dynamic classes from all resource packages in dependencies.
    ResourceClassRegistry registry = ResourceClassRegistry.get(getProject());
    AndroidFacet facet = AndroidFacet.getInstance(module);
    assertThat(facet).isNotNull();
    ResourceRepositoryManager repositoryManager = ResourceRepositoryManager.getInstance(facet);
    assertThat(repositoryManager).isNotNull();
    assertThat(registry.findClassDefinition("com.google.example.resource.R", repositoryManager))
        .isNotNull();
    assertThat(
            registry.findClassDefinition("com.google.example.resource.R$string", repositoryManager))
        .isNotNull();
    assertThat(registry.findClassDefinition("com.google.example.resource2.R", repositoryManager))
        .isNotNull();
    assertThat(
            registry.findClassDefinition(
                "com.google.example.resource2.R$layout", repositoryManager))
        .isNotNull();
    assertThat(
            registry.findClassDefinition(
                "com.google.example.transitive.resource.R", repositoryManager))
        .isNotNull();
    assertThat(
            registry.findClassDefinition(
                "com.google.example.transitive.resource.R$style", repositoryManager))
        .isNotNull();

    // And nothing else.
    assertThat(
            registry.findClassDefinition("com.google.example.main.MainActivity", repositoryManager))
        .isNull();
    assertThat(registry.findClassDefinition("com.google.example.resource.Bogus", repositoryManager))
        .isNull();
    assertThat(registry.findClassDefinition("com.google.example.main.R", repositoryManager))
        .isNull();
    assertThat(registry.findClassDefinition("com.google.example.main.R$string", repositoryManager))
        .isNull();
    assertThat(registry.findClassDefinition("com.google.example.java.Java", repositoryManager))
        .isNull();
    assertThat(registry.findClassDefinition("com.google.unrelated.R", repositoryManager)).isNull();
    assertThat(registry.findClassDefinition("com.google.unrelated.R$layout", repositoryManager))
        .isNull();
  }

  private void createClassesInJars() {
    fileSystem.createFile(
        BLAZE_BIN
            + "/com/google/example/libmain.jar!"
            + "/com/google/example/main/MainActivity.class");
    fileSystem.createFile(
        BLAZE_BIN + "/com/google/example/main_resources.jar!" + "/com/google/example/main/R.class");
    fileSystem.createFile(
        BLAZE_BIN
            + "/com/google/example/main_resources.jar!"
            + "/com/google/example/main/R$string.class");
    fileSystem.createFile(
        BLAZE_BIN
            + "/com/google/example/libandroid.jar!"
            + "/com/google/example/android/Android.class");
    fileSystem.createFile(
        BLAZE_BIN
            + "/com/google/example/resource_resources.jar!"
            + "/com/google/example/resource/R.class");
    fileSystem.createFile(
        BLAZE_BIN
            + "/com/google/example/resource_resources.jar!"
            + "/com/google/example/resource/R$style.class");
    fileSystem.createFile(
        BLAZE_BIN
            + "/com/google/unrelated/resource_resources.jar!"
            + "/com/google/unrelated/resource/R$layout.class");
    fileSystem.createFile(
        BLAZE_BIN
            + "/com/google/example/resource2_resources.jar!"
            + "/com/google/example/resource2/R.class");
    fileSystem.createFile(
        BLAZE_BIN
            + "/com/google/example/transitive/resource_resources.jar!"
            + "/com/google/example/transitive/R.class");
    fileSystem.createFile(
        BLAZE_BIN + "/com/google/example/libjava.jar!" + "/com/google/example/java/Java.class");
    fileSystem.createFile(
        BLAZE_BIN
            + "/com/google/example/libtransitive.jar!"
            + "/com/google/example/transitive/Transitive.class");
    fileSystem.createFile(
        BLAZE_BIN
            + "/com/google/example/libshared.jar!"
            + "/com/google/example/shared/Shared.class");
    fileSystem.createFile(
        BLAZE_BIN
            + "/com/google/example/libshared2.jar!"
            + "/com/google/example/shared2/Shared2.class");
    fileSystem.createFile(
        "com/google/example/libimport.jar!" + "/com/google/example/import/Import.class");
    fileSystem.createFile(
        "com/google/example/transitive/libimport.jar!"
            + "/com/google/example/transitive/import/TransitiveImport.class");
    fileSystem.createFile(
        BLAZE_BIN
            + "/com/google/example/unrelated/libjava.jar!"
            + "/com/google/example/unrelated/Java.class");
    fileSystem.createFile(
        BLAZE_BIN
            + "/com/google/example/unrelated/libandroid.jar!"
            + "/com/google/example/unrelated/Android.class");
    fileSystem.createFile(
        BLAZE_BIN
            + "/com/google/example/unrelated/libresource.jar!"
            + "/com/google/example/unrelated/Resource.class");
    fileSystem.createFile(
        BLAZE_BIN
            + "/com/google/example/unrelated/resource_resources.jar!"
            + "/com/google/example/unrelated/R.class");
  }

  private TargetMap buildTargetMap() {
    Label mainResourceLibrary = Label.create("//com/google/example:main");
    Label androidDependency = Label.create("//com/google/example:android");
    Label resourceDependency = Label.create("//com/google/example:resource");
    Label resourceDependency2 = Label.create("//com/google/example:resource2");
    Label transitiveResourceDependency = Label.create("//com/google/example/transitive:resource");
    Label javaDependency = Label.create("//com/google/example:java");
    Label transitiveJavaDependency = Label.create("//com/google/example:transitive");
    Label sharedJavaDependency = Label.create("//com/google/example:shared");
    Label sharedJavaDependency2 = Label.create("//com/google/example:shared2");
    Label importDependency = Label.create("//com/google/example:import");
    Label transitiveImportDependency = Label.create("//com/google/example/transitive:import");
    Label unrelatedJava = Label.create("//com/google/unrelated:java");
    Label unrelatedAndroid = Label.create("//com/google/unrelated:android");
    Label unrelatedResource = Label.create("//com/google/unrelated:resource");

    AndroidResourceModuleRegistry registry = new AndroidResourceModuleRegistry();
    registry.put(
        module,
        AndroidResourceModule.builder(TargetKey.forPlainTarget(mainResourceLibrary)).build());
    // Not using these, but they should be in the registry.
    registry.put(
        mock(Module.class),
        AndroidResourceModule.builder(TargetKey.forPlainTarget(resourceDependency)).build());
    registry.put(
        mock(Module.class),
        AndroidResourceModule.builder(TargetKey.forPlainTarget(resourceDependency2)).build());
    registry.put(
        mock(Module.class),
        AndroidResourceModule.builder(TargetKey.forPlainTarget(transitiveResourceDependency))
            .build());
    registry.put(
        mock(Module.class),
        AndroidResourceModule.builder(TargetKey.forPlainTarget(unrelatedResource)).build());
    registerProjectService(AndroidResourceModuleRegistry.class, registry);

    return TargetMapBuilder.builder()
        .addTarget(
            TargetIdeInfo.builder()
                .setLabel(mainResourceLibrary)
                .setKind(Kind.fromRuleName("android_library"))
                .setJavaInfo(
                    javaInfoWithJars(
                        "com/google/example/libmain.jar", "com/google/example/main_resources.jar"))
                .setAndroidInfo(
                    androidInfoWithResourceAndJar(
                        "com.google.example.main",
                        "com/google/example/main/res",
                        "com/google/example/main_resources.jar"))
                .addDependency(androidDependency)
                .addDependency(resourceDependency)
                .addDependency(resourceDependency2)
                .addDependency(javaDependency)
                .addDependency(importDependency))
        .addTarget(
            TargetIdeInfo.builder()
                .setLabel(androidDependency)
                .setKind(Kind.fromRuleName("android_library"))
                .setJavaInfo(javaInfoWithJars("com/google/example/libandroid.jar"))
                .addDependency(transitiveResourceDependency))
        .addTarget(
            TargetIdeInfo.builder()
                .setLabel(resourceDependency)
                .setKind(Kind.fromRuleName("android_library"))
                .setJavaInfo(
                    javaInfoWithJars(
                        "com/google/example/resource.jar",
                        "com/google/example/resource_resources.jar"))
                .setAndroidInfo(
                    androidInfoWithResourceAndJar(
                        "com.google.example.resource",
                        "com/google/example/resource/res",
                        "com/google/example/resource_resources.jar")))
        .addTarget(
            TargetIdeInfo.builder()
                .setLabel(resourceDependency2)
                .setKind(Kind.fromRuleName("android_library"))
                .setJavaInfo(
                    javaInfoWithJars(
                        "com/google/example/resource2.jar",
                        "com/google/example/resource2_resources.jar"))
                .setAndroidInfo(
                    androidInfoWithResourceAndJar(
                        "com.google.example.resource2",
                        "com/google/example/resource2/res",
                        "com/google/example/resource2_resources.jar")))
        .addTarget(
            TargetIdeInfo.builder()
                .setLabel(transitiveResourceDependency)
                .setKind(Kind.fromRuleName("android_library"))
                .setJavaInfo(
                    javaInfoWithJars(
                        "com/google/example/transitive/resource.jar",
                        "com/google/example/transitive/resource_resources.jar"))
                .setAndroidInfo(
                    androidInfoWithResourceAndJar(
                        "com.google.example.transitive.resource",
                        "com/google/example/transitive/resource/res",
                        "com/google/example/transitive/resource_resources.jar")))
        .addTarget(
            TargetIdeInfo.builder()
                .setLabel(javaDependency)
                .setKind(Kind.fromRuleName("java_library"))
                .setJavaInfo(javaInfoWithJars("com/google/example/libjava.jar"))
                .addDependency(transitiveJavaDependency)
                .addDependency(sharedJavaDependency)
                .addDependency(sharedJavaDependency2)
                .addDependency(transitiveImportDependency))
        .addTarget(
            TargetIdeInfo.builder()
                .setLabel(transitiveJavaDependency)
                .setKind(Kind.fromRuleName("java_library"))
                .setJavaInfo(javaInfoWithJars("com/google/example/libtransitive.jar"))
                .addDependency(sharedJavaDependency)
                .addDependency(sharedJavaDependency2))
        .addTarget(
            TargetIdeInfo.builder()
                .setLabel(sharedJavaDependency)
                .setKind(Kind.fromRuleName("java_library"))
                .setJavaInfo(javaInfoWithJars("com/google/example/libshared.jar"))
                .addDependency(sharedJavaDependency2))
        .addTarget(
            TargetIdeInfo.builder()
                .setLabel(sharedJavaDependency2)
                .setKind(Kind.fromRuleName("java_library"))
                .setJavaInfo(javaInfoWithJars("com/google/example/libshared2.jar")))
        .addTarget(
            TargetIdeInfo.builder()
                .setLabel(importDependency)
                .setKind(Kind.fromRuleName("java_import"))
                .setJavaInfo(javaInfoWithCheckedInJars("com/google/example/libimport.jar")))
        .addTarget(
            TargetIdeInfo.builder()
                .setLabel(transitiveImportDependency)
                .setKind(Kind.fromRuleName("java_import"))
                .setJavaInfo(
                    javaInfoWithCheckedInJars(
                        "com/google/example/transitive/libimport.jar",
                        "com/google/example/transitive/libimport2.jar")))
        .addTarget(
            TargetIdeInfo.builder()
                .setLabel(unrelatedJava)
                .setKind(Kind.fromRuleName("java_library"))
                .setJavaInfo(javaInfoWithJars("com/google/unrelated/libjava.jar")))
        .addTarget(
            TargetIdeInfo.builder()
                .setLabel(unrelatedAndroid)
                .setKind(Kind.fromRuleName("android_library"))
                .setJavaInfo(javaInfoWithJars("com/google/unrelated/libandroid.jar")))
        .addTarget(
            TargetIdeInfo.builder()
                .setLabel(unrelatedResource)
                .setKind(Kind.fromRuleName("android_library"))
                .setJavaInfo(
                    javaInfoWithJars(
                        "com/google/unrelated/libresource.jar",
                        "com/google/unrelated/resource_resources.jar"))
                .setAndroidInfo(
                    androidInfoWithResourceAndJar(
                        "com.google.unrelated.resource",
                        "com/google/unrelated/resource/res",
                        "com/google/unrelated/resource_resources.jar")))
        .build();
  }

  private JavaIdeInfo.Builder javaInfoWithJars(String... relativeJarPaths) {
    JavaIdeInfo.Builder builder = JavaIdeInfo.builder();
    for (String relativeJarPath : relativeJarPaths) {
      ArtifactLocation jar =
          ArtifactLocation.builder()
              .setRootExecutionPathFragment(BLAZE_BIN)
              .setRelativePath(relativeJarPath)
              .setIsSource(false)
              .build();
      builder.addJar(LibraryArtifact.builder().setClassJar(jar));
      fileSystem.createFile(jar.getExecutionRootRelativePath());
    }
    return builder;
  }

  private JavaIdeInfo.Builder javaInfoWithCheckedInJars(String... relativeJarPaths) {
    JavaIdeInfo.Builder builder = JavaIdeInfo.builder();
    for (String relativeJarPath : relativeJarPaths) {
      ArtifactLocation jar =
          ArtifactLocation.builder().setRelativePath(relativeJarPath).setIsSource(true).build();
      builder.addJar(LibraryArtifact.builder().setClassJar(jar));
      fileSystem.createFile(jar.getExecutionRootRelativePath());
    }
    return builder;
  }

  private static AndroidIdeInfo.Builder androidInfoWithResourceAndJar(
      String javaPackage, String relativeResourcePath, String relativeJarPath) {
    return AndroidIdeInfo.builder()
        .setGenerateResourceClass(true)
        .setResourceJavaPackage(javaPackage)
        .addResource(
            ArtifactLocation.builder()
                .setRelativePath(relativeResourcePath)
                .setIsSource(true)
                .build())
        .setResourceJar(
            LibraryArtifact.builder()
                .setClassJar(
                    // No need to createFile for this one since it should also be in the Java info.
                    ArtifactLocation.builder()
                        .setRootExecutionPathFragment(BLAZE_BIN)
                        .setRelativePath(relativeJarPath)
                        .setIsSource(false)
                        .build()));
  }

  private static class MockAndroidFacet extends AndroidFacet {
    public MockAndroidFacet(Module module) {
      super(module, AndroidFacet.NAME, new AndroidFacetConfiguration());
    }

    @Override
    public void initFacet() {
      // We don't need this, but it causes trouble when it tries looking for project templates.
    }
  }
}
