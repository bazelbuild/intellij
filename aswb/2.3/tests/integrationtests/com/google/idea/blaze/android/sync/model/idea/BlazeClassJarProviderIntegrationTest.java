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

import com.android.tools.idea.model.ClassJarProvider;
import com.android.tools.idea.res.AppResourceRepository;
import com.android.tools.idea.res.ResourceClassRegistry;
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
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.sync.workspace.ArtifactLocationDecoder;
import com.intellij.facet.FacetManager;
import com.intellij.facet.ModifiableFacetModel;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFile;
import java.io.File;
import java.io.IOException;
import java.util.List;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.AndroidFacetConfiguration;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Integration test for {@link BlazeClassJarProvider}. */
@RunWith(JUnit4.class)
public class BlazeClassJarProviderIntegrationTest extends BlazeIntegrationTestCase {
  private static final String BLAZE_BIN = "blaze-out/crosstool/bin";

  private Module module;
  private ClassJarProvider classJarProvider;

  @Before
  public void doSetup() {
    module = testFixture.getModule();

    ArtifactLocationDecoder decoder =
        (location) -> new File("/src", location.getExecutionRootRelativePath());

    mockBlazeProjectDataManager(
        new BlazeProjectData(
            0L, buildTargetMap(), null, null, null, null, decoder, null, null, null));
    classJarProvider = new BlazeClassJarProvider(getProject());
  }

  @Test
  public void testFindModuleClassFile() {
    createClassesInJars();

    // Make sure we can find classes in the main resource module.
    assertThat(classJarProvider.findModuleClassFile("com.google.example.main.MainActivity", module))
        .isEqualTo(
            fileSystem.findFile(
                BLAZE_BIN
                    + "/com/google/example/main.jar!"
                    + "/com/google/example/main/MainActivity.class"));
    assertThat(classJarProvider.findModuleClassFile("com.google.example.main.R", module))
        .isEqualTo(
            fileSystem.findFile(
                BLAZE_BIN
                    + "/com/google/example/main_resources.jar!"
                    + "/com/google/example/main/R.class"));
    assertThat(classJarProvider.findModuleClassFile("com.google.example.main.R$string", module))
        .isEqualTo(
            fileSystem.findFile(
                BLAZE_BIN
                    + "/com/google/example/main_resources.jar!"
                    + "/com/google/example/main/R$string.class"));

    // And not classes that are missing.
    assertThat(classJarProvider.findModuleClassFile("com.google.example.main.MissingClass", module))
        .isNull();
    assertThat(classJarProvider.findModuleClassFile("com.google.example.main.R$missing", module))
        .isNull();

    // And not classes in other libraries.
    assertThat(classJarProvider.findModuleClassFile("com.google.example.java.CustomView", module))
        .isNull();
    assertThat(classJarProvider.findModuleClassFile("com.google.example.android_res.R", module))
        .isNull();
    assertThat(
            classJarProvider.findModuleClassFile("com.google.example.android_res.R$style", module))
        .isNull();
    assertThat(
            classJarProvider.findModuleClassFile(
                "com.google.unrelated.android_res.R$layout", module))
        .isNull();
  }

  @Test
  public void testMissingMainJar() {
    createClassesInJars();

    ApplicationManager.getApplication()
        .runWriteAction(
            () -> {
              try {
                // Let's pretend that this hasn't been built yet.
                fileSystem.findFile(BLAZE_BIN + "/com/google/example/main.jar").delete(this);
              } catch (IOException ignored) {
                // ignored
              }
            });
    // This hasn't been built yet, and shouldn't be found.
    assertThat(classJarProvider.findModuleClassFile("com.google.example.main.MainActivity", module))
        .isNull();
    // But these should still be found.
    assertThat(classJarProvider.findModuleClassFile("com.google.example.main.R", module))
        .isEqualTo(
            fileSystem.findFile(
                BLAZE_BIN
                    + "/com/google/example/main_resources.jar!"
                    + "/com/google/example/main/R.class"));
    assertThat(classJarProvider.findModuleClassFile("com.google.example.main.R$string", module))
        .isEqualTo(
            fileSystem.findFile(
                BLAZE_BIN
                    + "/com/google/example/main_resources.jar!"
                    + "/com/google/example/main/R$string.class"));
  }

  @Test
  public void testGetModuleExternalLibraries() {
    // Need AndroidFact for AppResourceRepository.
    ApplicationManager.getApplication()
        .runWriteAction(
            () -> {
              ModifiableFacetModel model = FacetManager.getInstance(module).createModifiableModel();
              model.addFacet(new MockAndroidFacet(module));
              model.commit();
            });

    List<VirtualFile> externalLibraries = classJarProvider.getModuleExternalLibraries(module);
    assertThat(externalLibraries)
        .containsExactly(
            fileSystem.findFile(BLAZE_BIN + "/com/google/example/android_lib.jar"),
            fileSystem.findFile(BLAZE_BIN + "/com/google/example/android_res.jar"),
            fileSystem.findFile(BLAZE_BIN + "/com/google/example/android_res2.jar"),
            fileSystem.findFile(BLAZE_BIN + "/com/google/example/transitive/android_res.jar"),
            fileSystem.findFile(BLAZE_BIN + "/com/google/example/java.jar"),
            fileSystem.findFile(BLAZE_BIN + "/com/google/example/transitive/java.jar"),
            fileSystem.findFile(BLAZE_BIN + "/com/google/example/shared/java.jar"),
            fileSystem.findFile(BLAZE_BIN + "/com/google/example/shared2/java.jar"),
            fileSystem.findFile("com/google/example/import.jar"),
            fileSystem.findFile("com/google/example/transitive/import.jar"),
            fileSystem.findFile("com/google/example/transitive/import2.jar"));

    // Make sure we can generate dynamic classes from all resource packages in dependencies.
    ResourceClassRegistry registry = ResourceClassRegistry.get(getProject());
    AppResourceRepository repository = AppResourceRepository.getAppResources(module, false);
    assertThat(repository).isNotNull();
    assertThat(registry.findClassDefinition("com.google.example.android_res.R", repository))
        .isNotNull();
    assertThat(registry.findClassDefinition("com.google.example.android_res.R$string", repository))
        .isNotNull();
    assertThat(registry.findClassDefinition("com.google.example.android_res2.R", repository))
        .isNotNull();
    assertThat(registry.findClassDefinition("com.google.example.android_res2.R$layout", repository))
        .isNotNull();
    assertThat(
            registry.findClassDefinition("com.google.example.transitive.android_res.R", repository))
        .isNotNull();
    assertThat(
            registry.findClassDefinition(
                "com.google.example.transitive.android_res.R$style", repository))
        .isNotNull();

    // And nothing else.
    assertThat(registry.findClassDefinition("com.google.example.main.MainActivity", repository))
        .isNull();
    assertThat(registry.findClassDefinition("com.google.example.android_res.Bogus", repository))
        .isNull();
    assertThat(registry.findClassDefinition("com.google.example.main.R", repository)).isNull();
    assertThat(registry.findClassDefinition("com.google.example.main.R$string", repository))
        .isNull();
    assertThat(registry.findClassDefinition("com.google.example.java.CustomView", repository))
        .isNull();
    assertThat(registry.findClassDefinition("com.google.unrelated.android_res.R", repository))
        .isNull();
    assertThat(
            registry.findClassDefinition("com.google.unrelated.android_res.R$layout", repository))
        .isNull();
  }

  @Test
  public void testMissingExternalJars() {
    ApplicationManager.getApplication()
        .runWriteAction(
            () -> {
              try {
                // Let's pretend that these haven't been built yet.
                fileSystem.findFile(BLAZE_BIN + "/com/google/example/java.jar").delete(this);
                fileSystem
                    .findFile(BLAZE_BIN + "/com/google/example/android_res2.jar")
                    .delete(this);
                fileSystem
                    .findFile(BLAZE_BIN + "/com/google/example/shared2/java.jar")
                    .delete(this);
              } catch (IOException ignored) {
                // ignored
              }
            });
    List<VirtualFile> externalLibraries = classJarProvider.getModuleExternalLibraries(module);
    assertThat(externalLibraries)
        .containsExactly(
            fileSystem.findFile(BLAZE_BIN + "/com/google/example/android_lib.jar"),
            fileSystem.findFile(BLAZE_BIN + "/com/google/example/android_res.jar"),
            // This should be missing.
            // fileSystem.findFile(BLAZE_BIN + "/com/google/example/android_res2.jar"),
            fileSystem.findFile(BLAZE_BIN + "/com/google/example/transitive/android_res.jar"),
            // This should be missing.
            // fileSystem.findFile(BLAZE_BIN + "/com/google/example/java.jar"),
            fileSystem.findFile(BLAZE_BIN + "/com/google/example/transitive/java.jar"),
            fileSystem.findFile(BLAZE_BIN + "/com/google/example/shared/java.jar"),
            // This should be missing.
            // fileSystem.findFile(BLAZE_BIN + "/com/google/example/shared2/java.jar"),
            fileSystem.findFile("com/google/example/import.jar"),
            fileSystem.findFile("com/google/example/transitive/import.jar"),
            fileSystem.findFile("com/google/example/transitive/import2.jar"));
  }

  private void createClassesInJars() {
    fileSystem.createFile(
        BLAZE_BIN
            + "/com/google/example/main.jar!"
            + "/com/google/example/main/MainActivity.class");
    fileSystem.createFile(
        BLAZE_BIN + "/com/google/example/main_resources.jar!" + "/com/google/example/main/R.class");
    fileSystem.createFile(
        BLAZE_BIN
            + "/com/google/example/main_resources.jar!"
            + "/com/google/example/main/R$string.class");
    fileSystem.createFile(
        BLAZE_BIN + "/com/google/example/java.jar!" + "/com/google/example/java/CustomView.class");
    fileSystem.createFile(
        BLAZE_BIN
            + "/com/google/example/android_res_resources.jar!"
            + "/com/google/example/android_res/R.class");
    fileSystem.createFile(
        BLAZE_BIN
            + "/com/google/example/android_res_resources.jar!"
            + "/com/google/example/android_res/R$style.class");
    fileSystem.createFile(
        BLAZE_BIN
            + "/com/google/unrelated/android_res_resources.jar!"
            + "/com/google/unrelated/android_res/R$layout.class");
  }

  private TargetMap buildTargetMap() {
    Label mainResourceLibrary = new Label("//com/google/example:main");
    Label androidLibraryDependency = new Label("//com/google/example:android_lib");
    Label androidResourceDependency = new Label("//com/google/example:android_res");
    Label androidResourceDependency2 = new Label("//com/google/example:android_res2");
    Label transitiveResourceDependency = new Label("//com/google/example/transitive:android_res");
    Label javaDependency = new Label("//com/google/example:java");
    Label transitiveJavaDependency = new Label("//com/google/example/transitive:java");
    Label sharedJavaDependency = new Label("//com/google/example/shared:java");
    Label sharedJavaDependency2 = new Label("//com/google/example/shared2:java");
    Label importDependency = new Label("//com/google/example:import");
    Label transitiveImportDependency = new Label("//com/google/example/transitive:import");
    Label unrelatedJava = new Label("//com/google/unrelated:java");
    Label unrelatedAndroidLibrary = new Label("//com/google/unrelated:android_lib");
    Label unrelatedAndroidResource = new Label("//com/google/unrelated:android_res");

    AndroidResourceModuleRegistry registry = new AndroidResourceModuleRegistry();
    registry.put(
        module,
        AndroidResourceModule.builder(TargetKey.forPlainTarget(mainResourceLibrary)).build());
    // Not using these, but they should be in the registry.
    registry.put(
        mock(Module.class),
        AndroidResourceModule.builder(TargetKey.forPlainTarget(androidResourceDependency)).build());
    registry.put(
        mock(Module.class),
        AndroidResourceModule.builder(TargetKey.forPlainTarget(androidResourceDependency2))
            .build());
    registry.put(
        mock(Module.class),
        AndroidResourceModule.builder(TargetKey.forPlainTarget(transitiveResourceDependency))
            .build());
    registry.put(
        mock(Module.class),
        AndroidResourceModule.builder(TargetKey.forPlainTarget(unrelatedAndroidResource)).build());
    registerProjectService(AndroidResourceModuleRegistry.class, registry);

    return TargetMapBuilder.builder()
        .addTarget(
            TargetIdeInfo.builder()
                .setLabel(mainResourceLibrary)
                .setKind(Kind.ANDROID_LIBRARY)
                .setJavaInfo(
                    javaInfoWithJars(
                        "com/google/example/main.jar", "com/google/example/main_resources.jar"))
                .setAndroidInfo(
                    androidInfoWithResourceAndJar(
                        "com.google.example.main",
                        "com/google/example/main/res",
                        "com/google/example/main_resources.jar"))
                .addDependency(androidLibraryDependency)
                .addDependency(androidResourceDependency)
                .addDependency(androidResourceDependency2)
                .addDependency(javaDependency)
                .addDependency(importDependency))
        .addTarget(
            TargetIdeInfo.builder()
                .setLabel(androidLibraryDependency)
                .setKind(Kind.ANDROID_LIBRARY)
                .setJavaInfo(javaInfoWithJars("com/google/example/android_lib.jar"))
                .addDependency(transitiveResourceDependency))
        .addTarget(
            TargetIdeInfo.builder()
                .setLabel(androidResourceDependency)
                .setKind(Kind.ANDROID_LIBRARY)
                .setJavaInfo(
                    javaInfoWithJars(
                        "com/google/example/android_res.jar",
                        "com/google/example/android_res_resources.jar"))
                .setAndroidInfo(
                    androidInfoWithResourceAndJar(
                        "com.google.example.android_res",
                        "com/google/example/android_res/res",
                        "com/google/example/android_res_resources.jar")))
        .addTarget(
            TargetIdeInfo.builder()
                .setLabel(androidResourceDependency2)
                .setKind(Kind.ANDROID_LIBRARY)
                .setJavaInfo(
                    javaInfoWithJars(
                        "com/google/example/android_res2.jar",
                        "com/google/example/android_res2_resources.jar"))
                .setAndroidInfo(
                    androidInfoWithResourceAndJar(
                        "com.google.example.android_res2",
                        "com/google/example/android_res2/res",
                        "com/google/example/android_res2_resources.jar")))
        .addTarget(
            TargetIdeInfo.builder()
                .setLabel(transitiveResourceDependency)
                .setKind(Kind.ANDROID_LIBRARY)
                .setJavaInfo(
                    javaInfoWithJars(
                        "com/google/example/transitive/android_res.jar",
                        "com/google/example/transitive/android_res_resources.jar"))
                .setAndroidInfo(
                    androidInfoWithResourceAndJar(
                        "com.google.example.transitive.android_res",
                        "com/google/example/transitive/android_res/res",
                        "com/google/example/transitive/android_res_resources.jar")))
        .addTarget(
            TargetIdeInfo.builder()
                .setLabel(javaDependency)
                .setKind(Kind.JAVA_LIBRARY)
                .setJavaInfo(javaInfoWithJars("com/google/example/java.jar"))
                .addDependency(transitiveJavaDependency)
                .addDependency(sharedJavaDependency)
                .addDependency(sharedJavaDependency2)
                .addDependency(transitiveImportDependency))
        .addTarget(
            TargetIdeInfo.builder()
                .setLabel(transitiveJavaDependency)
                .setKind(Kind.JAVA_LIBRARY)
                .setJavaInfo(javaInfoWithJars("com/google/example/transitive/java.jar"))
                .addDependency(sharedJavaDependency)
                .addDependency(sharedJavaDependency2))
        .addTarget(
            TargetIdeInfo.builder()
                .setLabel(sharedJavaDependency)
                .setKind(Kind.JAVA_LIBRARY)
                .setJavaInfo(javaInfoWithJars("com/google/example/shared/java.jar"))
                .addDependency(sharedJavaDependency2))
        .addTarget(
            TargetIdeInfo.builder()
                .setLabel(sharedJavaDependency2)
                .setKind(Kind.JAVA_LIBRARY)
                .setJavaInfo(javaInfoWithJars("com/google/example/shared2/java.jar")))
        .addTarget(
            TargetIdeInfo.builder()
                .setLabel(importDependency)
                .setKind(Kind.JAVA_IMPORT)
                .setJavaInfo(javaInfoWithCheckedInJars("com/google/example/import.jar")))
        .addTarget(
            TargetIdeInfo.builder()
                .setLabel(transitiveImportDependency)
                .setKind(Kind.JAVA_IMPORT)
                .setJavaInfo(
                    javaInfoWithCheckedInJars(
                        "com/google/example/transitive/import.jar",
                        "com/google/example/transitive/import2.jar")))
        .addTarget(
            TargetIdeInfo.builder()
                .setLabel(unrelatedJava)
                .setKind(Kind.JAVA_LIBRARY)
                .setJavaInfo(javaInfoWithJars("com/google/unrelated/java.jar")))
        .addTarget(
            TargetIdeInfo.builder()
                .setLabel(unrelatedAndroidLibrary)
                .setKind(Kind.ANDROID_LIBRARY)
                .setJavaInfo(javaInfoWithJars("com/google/unrelated/android_lib.jar")))
        .addTarget(
            TargetIdeInfo.builder()
                .setLabel(unrelatedAndroidResource)
                .setKind(Kind.ANDROID_LIBRARY)
                .setJavaInfo(
                    javaInfoWithJars(
                        "com/google/unrelated/android_res.jar",
                        "com/google/unrelated/android_res_resources.jar"))
                .setAndroidInfo(
                    androidInfoWithResourceAndJar(
                        "com.google.unrelated.android_res",
                        "com/google/unrelated/android_res/res",
                        "com/google/unrelated/android_res_resources.jar")))
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
