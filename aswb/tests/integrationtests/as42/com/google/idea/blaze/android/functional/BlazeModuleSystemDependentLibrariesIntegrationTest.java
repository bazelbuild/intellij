/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.android.functional;

import static com.google.common.truth.Truth.assertThat;
import static com.google.idea.blaze.android.targetmapbuilder.NbAarTarget.aar_import;
import static com.google.idea.blaze.android.targetmapbuilder.NbAndroidTarget.android_binary;
import static com.google.idea.blaze.android.targetmapbuilder.NbAndroidTarget.android_library;
import static com.google.idea.blaze.android.targetmapbuilder.NbJavaTarget.java_library;

import com.android.ide.common.repository.GradleCoordinate;
import com.android.ide.common.util.PathString;
import com.android.projectmodel.ExternalLibrary;
import com.android.projectmodel.ExternalLibraryImpl;
import com.android.projectmodel.SelectiveResourceFolder;
import com.android.tools.idea.projectsystem.GoogleMavenArtifactId;
import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.android.BlazeAndroidIntegrationTestCase;
import com.google.idea.blaze.android.MockSdkUtil;
import com.google.idea.blaze.android.libraries.AarLibraryFileBuilder;
import com.google.idea.blaze.android.libraries.UnpackedAars;
import com.google.idea.blaze.android.projectsystem.BlazeModuleSystem;
import com.google.idea.blaze.android.projectsystem.MavenArtifactLocator;
import com.google.idea.blaze.android.targetmapbuilder.NbAarTarget;
import com.google.idea.blaze.android.targetmapbuilder.NbAndroidTarget;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.io.FileOperationProvider;
import com.google.idea.blaze.base.model.LibraryKey;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.settings.BuildSystem;
import com.google.idea.blaze.base.sync.data.BlazeDataStorage;
import com.google.idea.common.experiments.ExperimentService;
import com.google.idea.common.experiments.MockExperimentService;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Integration test for {@link BlazeModuleSystem#getResolvedDependentLibraries()}. */
@RunWith(JUnit4.class)
public class BlazeModuleSystemDependentLibrariesIntegrationTest
    extends BlazeAndroidIntegrationTestCase {
  private BlazeModuleSystem appModuleSystem;
  private BlazeModuleSystem workspaceModuleSystem;

  @Before
  public void setup() {
    final String recyclerView = "//third_party/recyclerview:recyclerview";
    final String constraintLayout = "//third_party/constraint_layout:constraint_layout";
    final String quantum = "//third_party/quantum:values";
    final String aarFile = "//third_party/aar:an_aar";
    final String individualLibrary = "//third_party/individualLibrary:values";
    final String guava = "//third_party/guava:java";
    final String main = "//java/com/google:app";
    final String intermediateDependency = "//java/com/google/intermediate:intermediate";

    // BlazeAndroidRunConfigurationCommonState.isNativeDebuggingEnabled() always
    // returns false if this experiment is false. Enable it by setting it to true.
    MockExperimentService experimentService = new MockExperimentService();
    registerApplicationComponent(ExperimentService.class, experimentService);

    registerExtension(
        MavenArtifactLocator.EP_NAME,
        new MavenArtifactLocator() {
          @Override
          public Label labelFor(GradleCoordinate coordinate) {
            switch (GoogleMavenArtifactId.forCoordinate(coordinate)) {
              case RECYCLERVIEW_V7:
                return Label.create("//third_party/recyclerview:recyclerview");
              case CONSTRAINT_LAYOUT:
                return Label.create("//third_party/constraint_layout:constraint_layout");
              default:
                return null;
            }
          }

          @Override
          public BuildSystem buildSystem() {
            return BuildSystem.Blaze;
          }
        });

    // This MainActivity.java file is needed because blaze sync will fail if the source
    // directory is empty, so we put something there. The fact that it's MainActivity.java
    // doesn't mean anything.
    workspace.createFile(
        new WorkspacePath("java/com/google/app/MainActivity.java"),
        "package com.google.app",
        "import android.app.Activity;",
        "public class MainActivity extends Activity {",
        "}");

    // Make JARs appear nonempty so that they aren't filtered out
    registerApplicationService(
        FileOperationProvider.class,
        new FileOperationProvider() {
          @Override
          public long getFileSize(File file) {
            return file.getName().endsWith("jar") ? 500L : super.getFileSize(file);
          }
        });

    setProjectView(
        "directories:",
        "  java/com/google",
        "targets:",
        "  //java/com/google:app",
        "android_sdk_platform: android-27");
    MockSdkUtil.registerSdk(workspace, "27");

    NbAarTarget aarTarget =
        aar_import(aarFile)
            .aar("lib_aar.aar")
            .generated_jar("_aar/an_aar/classes_and_libs_merged.jar");
    AarLibraryFileBuilder.aar(workspaceRoot, aarTarget.getAar().getRelativePath())
        .src(
            "res/values/colors.xml",
            ImmutableList.of(
                "<?xml version=\"1.0\" encoding=\"utf-8\"?>",
                "<resources>",
                "    <color name=\"aarColor\">#ffffff</color>",
                "</resources>"))
        .build();

    NbAndroidTarget binaryTarget =
        android_binary(main)
            .source_jar("app.jar")
            .res("res")
            .res_folder("//third_party/shared/res", "app-third_party-shared-res.aar")
            .src("app/MainActivity.java")
            .dep(guava, quantum, aarFile, intermediateDependency);
    binaryTarget
        .getAarList()
        .forEach(aar -> AarLibraryFileBuilder.aar(workspaceRoot, aar.getRelativePath()).build());

    NbAndroidTarget quantumTarget =
        android_library(quantum)
            .res_folder("//third_party/quantum/res", "values-third_party-quantum-res.aar");
    quantumTarget
        .getAarList()
        .forEach(aar -> AarLibraryFileBuilder.aar(workspaceRoot, aar.getRelativePath()).build());

    NbAndroidTarget constraintLayoutTarget =
        android_library(constraintLayout)
            .res_folder(
                "//third_party/constraint_layout/res",
                "constraint_layout-third_party-constraint_layout-res.aar");
    constraintLayoutTarget
        .getAarList()
        .forEach(aar -> AarLibraryFileBuilder.aar(workspaceRoot, aar.getRelativePath()).build());

    setTargetMap(
        binaryTarget,
        android_library(individualLibrary).res("res"),
        java_library(guava).source_jar("//third_party/guava-21.jar"),
        quantumTarget,
        aarTarget,
        android_library(recyclerView).res("res"),
        android_library(intermediateDependency).res("res").dep(constraintLayout),
        constraintLayoutTarget);
    runFullBlazeSyncWithNoIssues();

    Module appModule =
        ModuleManager.getInstance(getProject()).findModuleByName("java.com.google.app");
    appModuleSystem = BlazeModuleSystem.getInstance(appModule);

    Module workspaceModule =
        ModuleManager.getInstance(getProject())
            .findModuleByName(BlazeDataStorage.WORKSPACE_MODULE_NAME);
    workspaceModuleSystem = BlazeModuleSystem.getInstance(workspaceModule);
  }

  private static ArtifactLocation source(String relativePath) {
    return ArtifactLocation.builder().setRelativePath(relativePath).setIsSource(true).build();
  }

  private ExternalLibrary getAarLibrary(
      PathString rootPath, String aarPath, @Nullable String resourcePackage) {
    String cacheKey = UnpackedAars.cacheKeyForAar(rootPath.resolve(aarPath).getNativePath());
    UnpackedAars unpackedAars = UnpackedAars.getInstance(getProject());
    PathString aarFile = new PathString(unpackedAars.getAarDir(cacheKey));
    PathString resFolder = new PathString(unpackedAars.getResourceDirectory(cacheKey));
    return new ExternalLibraryImpl(LibraryKey.libraryNameFromArtifactLocation(source(aarPath)))
        .withLocation(aarFile)
        .withManifestFile(
            resFolder == null ? null : resFolder.getParentOrRoot().resolve("AndroidManifest.xml"))
        .withResFolder(resFolder == null ? null : new SelectiveResourceFolder(resFolder, null))
        .withSymbolFile(resFolder == null ? null : resFolder.getParentOrRoot().resolve("R.txt"))
        .withPackageName(resourcePackage);
  }

  @Test
  public void getDependencies_multipleModulesGetSameLibraryInstances() {
    List<ExternalLibrary> workspaceModuleLibraries =
        workspaceModuleSystem.getDependentLibraries().stream()
            .filter(library -> library.getClassJars().isEmpty())
            .sorted(Comparator.comparing(ExternalLibrary::getAddress))
            .collect(Collectors.toList());
    List<ExternalLibrary> appModuleLibraries =
        appModuleSystem.getDependentLibraries().stream()
            .filter(library -> library.getClassJars().isEmpty())
            .sorted(Comparator.comparing(ExternalLibrary::getAddress))
            .collect(Collectors.toList());
    assertThat(workspaceModuleLibraries.size()).isEqualTo(appModuleLibraries.size());
    // Two modules depend on same resource libraries, so the reference of libraries should be the
    // same i.e. there should not be duplicate library instances
    for (int i = 0; i < workspaceModuleLibraries.size(); i++) {
      assertThat(workspaceModuleLibraries.get(i)).isSameAs(appModuleLibraries.get(i));
    }
  }

  @Test
  public void getDependencies_appModule() {
    PathString rootPath = new PathString(workspaceRoot.directory());
    Collection<ExternalLibrary> libraries = appModuleSystem.getDependentLibraries();
    assertThat(new ArrayList<>(libraries))
        .containsExactly(
            getAarLibrary(rootPath, "third_party/aar/lib_aar.aar", "third_party.aar"),
            getAarLibrary(rootPath, "java/com/google/app-third_party-shared-res.aar", "com.google"),
            getAarLibrary(
                rootPath,
                "third_party/constraint_layout/constraint_layout-third_party-constraint_layout-res.aar",
                "third_party.constraint_layout"),
            getAarLibrary(
                rootPath,
                "third_party/quantum/values-third_party-quantum-res.aar",
                "third_party.quantum"));

    assertThat(
            libraries.stream()
                .filter(library -> !library.getClassJars().isEmpty())
                .collect(Collectors.toList()))
        .isEmpty();
  }

  @Test
  public void getDependencies_workspaceModule() {
    PathString rootPath = new PathString(workspaceRoot.directory());
    Collection<ExternalLibrary> libraries = workspaceModuleSystem.getDependentLibraries();
    assertThat(new ArrayList<>(libraries))
        .containsExactly(
            getAarLibrary(
                rootPath,
                "third_party/quantum/values-third_party-quantum-res.aar",
                "third_party.quantum"),
            getAarLibrary(rootPath, "java/com/google/app-third_party-shared-res.aar", "com.google"),
            getAarLibrary(
                rootPath,
                "third_party/constraint_layout/constraint_layout-third_party-constraint_layout-res.aar",
                "third_party.constraint_layout"),
            getAarLibrary(rootPath, "third_party/aar/lib_aar.aar", "third_party.aar"),
            new ExternalLibraryImpl(
                LibraryKey.libraryNameFromArtifactLocation(source("third_party/guava-21.jar")),
                null,
                null,
                null,
                ImmutableList.of(rootPath.resolve("third_party/guava-21.jar")),
                ImmutableList.of(),
                null,
                null,
                null));
  }
}
