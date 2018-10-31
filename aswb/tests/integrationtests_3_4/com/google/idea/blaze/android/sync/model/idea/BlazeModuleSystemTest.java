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
package com.google.idea.blaze.android.sync.model.idea;

import static com.google.common.truth.Truth.assertThat;
import static com.google.idea.blaze.android.sync.importer.BlazeImportInput.createLooksLikeAarLibrary;
import static com.google.idea.blaze.android.sync.model.idea.BlazeImportFixture.source;
import static com.google.idea.blaze.base.sync.data.BlazeDataStorage.WORKSPACE_MODULE_NAME;
import static org.mockito.Mockito.mock;

import com.android.ide.common.repository.GradleCoordinate;
import com.android.projectmodel.ExternalLibrary;
import com.android.projectmodel.Library;
import com.android.tools.idea.projectsystem.GoogleMavenArtifactId;
import com.google.idea.blaze.android.AndroidIntegrationTestSetupRule;
import com.google.idea.blaze.android.projectsystem.BlazeModuleSystem;
import com.google.idea.blaze.android.projectsystem.MavenArtifactLocator;
import com.google.idea.blaze.android.sync.model.AndroidResourceModuleRegistry;
import com.google.idea.blaze.android.sync.model.BlazeAndroidImportResult;
import com.google.idea.blaze.android.sync.model.BlazeResourceLibrary;
import com.google.idea.blaze.android.sync.projectstructure.BlazeAndroidProjectStructureSyncer;
import com.google.idea.blaze.base.BlazeIntegrationTestCase;
import com.google.idea.blaze.base.ideinfo.TargetKey;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.LibraryKey;
import com.google.idea.blaze.base.model.MockBlazeProjectDataManager;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.common.experiments.ExperimentService;
import com.google.idea.common.experiments.MockExperimentService;
import com.intellij.openapi.module.Module;
import com.intellij.util.containers.hash.HashMap;
import java.util.Collection;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

/** Integration tests for {@link BlazeModuleSystem}. */
@RunWith(JUnit4.class)
public class BlazeModuleSystemTest extends BlazeIntegrationTestCase {

  @Rule
  public final AndroidIntegrationTestSetupRule androidSetupRule =
      new AndroidIntegrationTestSetupRule();

  private BlazeModuleSystem blazeModuleSystem;
  private Module module;
  private BlazeModuleSystem workspaceBlazeModuleSystem;
  private Module workspaceModule;
  private BlazeContext context;
  private BlazeImportFixture importFixture;
  private HashMap<String, Module> mockedModulesByName;

  private Module getMockModule(String moduleName) {
    Module module = mockedModulesByName.get(moduleName);
    if (module != null) {
      return module;
    }

    module = mock(Module.class, Mockito.CALLS_REAL_METHODS);
    Mockito.when(module.getName()).thenReturn(moduleName);
    Mockito.when(module.getProject()).thenReturn(getProject());
    mockedModulesByName.put(moduleName, module);
    return module;
  }

  private Module getMockModule(TargetKey targetKey) {
    return getMockModule(BlazeAndroidProjectStructureSyncer.moduleNameForAndroidModule(targetKey));
  }

  @Before
  public void doSetup() {
    mockedModulesByName = new HashMap<>();
    module = testFixture.getModule();
    workspaceModule = getMockModule(WORKSPACE_MODULE_NAME);
    context = new BlazeContext();
    MockExperimentService experimentService = new MockExperimentService();
    registerApplicationComponent(ExperimentService.class, experimentService);
    // BlazeAndroidRunConfigurationCommonState.isNativeDebuggingEnabled() always
    // returns false if this experiment is false.
    experimentService.setExperiment(createLooksLikeAarLibrary, true);

    importFixture = new BlazeImportFixture(getProject(), fileSystem, workspaceRoot, context);

    BlazeAndroidImportResult importAndroidResult = importFixture.importAndroidWorkspace();

    AndroidResourceModuleRegistry registry = new AndroidResourceModuleRegistry();
    importAndroidResult.androidResourceModules.forEach(
        androidResModule -> {
          if (androidResModule.targetKey.getLabel().targetName().toString().equals("app")) {
            registry.put(module, androidResModule);
          } else {
            registry.put(getMockModule(androidResModule.targetKey), androidResModule);
          }
        });
    registerProjectService(AndroidResourceModuleRegistry.class, registry);

    registerExtension(MavenArtifactLocator.EP_NAME, importFixture.getMavenArtifactLocator());

    BlazeProjectData blazeProjectData = importFixture.getBlazeProjectData();
    registerProjectService(
        BlazeProjectDataManager.class, new MockBlazeProjectDataManager(blazeProjectData));
    blazeModuleSystem = new BlazeModuleSystem(module);
    workspaceBlazeModuleSystem = new BlazeModuleSystem(workspaceModule);
  }

  @Test
  public void getDependencies_appModule() {
    Collection<Library> libraries = blazeModuleSystem.getResolvedDependentLibraries();
    assertThat(
            libraries.stream()
                .filter(library -> library instanceof ExternalLibrary)
                .map(library -> library.getAddress())
                .collect(Collectors.toList()))
        .containsExactly(
            BlazeResourceLibrary.libraryNameFromArtifactLocation(source("third_party/quantum/res")),
            BlazeResourceLibrary.libraryNameFromArtifactLocation(source("third_party/shared/res")),
            BlazeResourceLibrary.libraryNameFromArtifactLocation(
                source("third_party/constraint_layout/res")),
            LibraryKey.libraryNameFromArtifactLocation(source("third_party/aar/lib_aar.aar")));
    assertThat(
            libraries.stream()
                .filter(
                    library ->
                        library instanceof ExternalLibrary
                            && !((ExternalLibrary) library).getClassJars().isEmpty())
                .collect(Collectors.toList()))
        .isEmpty();
  }

  @Test
  public void getDependencies_workspaceModule() {
    Collection<Library> libraries = workspaceBlazeModuleSystem.getResolvedDependentLibraries();
    assertThat(
            libraries.stream()
                .filter(library -> library instanceof ExternalLibrary)
                .map(library -> library.getAddress())
                .collect(Collectors.toList()))
        .containsExactly(
            BlazeResourceLibrary.libraryNameFromArtifactLocation(source("third_party/quantum/res")),
            BlazeResourceLibrary.libraryNameFromArtifactLocation(source("third_party/shared/res")),
            BlazeResourceLibrary.libraryNameFromArtifactLocation(
                source("third_party/constraint_layout/res")),
            LibraryKey.libraryNameFromArtifactLocation(source("third_party/aar/lib_aar.aar")),
            LibraryKey.libraryNameFromArtifactLocation(source("third_party/guava-21.jar")));
  }

  @Test
  public void getResolvedDependency_missingDependency() {
    // The app module doesn't depend on recycler view
    GradleCoordinate recyclerView = GoogleMavenArtifactId.RECYCLERVIEW_V7.getCoordinate("+");
    assertThat(blazeModuleSystem.getResolvedDependency(recyclerView)).isNull();
  }

  @Test
  public void getResolvedDependency_transitiveDependency() {
    // The app module transitively depends on constraint layout because it depends on
    // //third_party/intermediate:intermediate
    GradleCoordinate constraintLayout = GoogleMavenArtifactId.CONSTRAINT_LAYOUT.getCoordinate("+");
    assertThat(blazeModuleSystem.getResolvedDependency(constraintLayout)).isNotNull();
  }

  @Test
  public void getRegisteredDependency_nullForMissingDependency() {
    // The app module doesn't depend on recycler view
    GradleCoordinate recyclerView = GoogleMavenArtifactId.RECYCLERVIEW_V7.getCoordinate("+");
    assertThat(blazeModuleSystem.getRegisteredDependency(recyclerView)).isNull();
  }

  @Test
  public void getResgisteredDependency_nullForTransitiveDependency() {
    // The app module transitively depends on constraint layout because it depends on
    // //third_party/intermediate:intermediate
    GradleCoordinate constraintLayout = GoogleMavenArtifactId.CONSTRAINT_LAYOUT.getCoordinate("+");
    assertThat(blazeModuleSystem.getRegisteredDependency(constraintLayout)).isNull();
  }

  @Test
  public void getRegisteredDependency_findsFirstLevelDependency() {
    TargetKey intermediate =
        TargetKey.forPlainTarget(Label.create("//java/com/google/intermediate:intermediate"));
    BlazeModuleSystem intermediateModuleSystem = new BlazeModuleSystem(getMockModule(intermediate));

    GradleCoordinate constraintLayout = GoogleMavenArtifactId.CONSTRAINT_LAYOUT.getCoordinate("+");
    assertThat(intermediateModuleSystem.getRegisteredDependency(constraintLayout)).isNotNull();
  }
}
