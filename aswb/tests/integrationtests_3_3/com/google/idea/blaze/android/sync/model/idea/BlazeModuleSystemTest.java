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

import com.android.projectmodel.ExternalLibrary;
import com.android.projectmodel.Library;
import com.google.idea.blaze.android.AndroidIntegrationTestSetupRule;
import com.google.idea.blaze.android.projectsystem.BlazeModuleSystem;
import com.google.idea.blaze.android.sync.model.AndroidResourceModuleRegistry;
import com.google.idea.blaze.android.sync.model.BlazeAndroidImportResult;
import com.google.idea.blaze.android.sync.model.BlazeResourceLibrary;
import com.google.idea.blaze.base.BlazeIntegrationTestCase;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.LibraryKey;
import com.google.idea.blaze.base.model.MockBlazeProjectDataManager;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.common.experiments.ExperimentService;
import com.google.idea.common.experiments.MockExperimentService;
import com.intellij.openapi.module.Module;
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

  @Before
  public void doSetup() {
    module = testFixture.getModule();
    workspaceModule = mock(Module.class, Mockito.CALLS_REAL_METHODS);
    Mockito.doReturn(WORKSPACE_MODULE_NAME).when(workspaceModule).getName();
    Mockito.doReturn(getProject()).when(workspaceModule).getProject();
    context = new BlazeContext();
    MockExperimentService experimentService = new MockExperimentService();
    registerApplicationComponent(ExperimentService.class, experimentService);
    // BlazeAndroidRunConfigurationCommonState.isNativeDebuggingEnabled() always
    // returns false if this experiment is false.
    experimentService.setExperiment(createLooksLikeAarLibrary, true);

    importFixture = new BlazeImportFixture(getProject(), fileSystem, workspaceRoot, context);

    BlazeAndroidImportResult importAndroidResult = importFixture.importAndroidWorkspace();

    AndroidResourceModuleRegistry registry = new AndroidResourceModuleRegistry();
    registry.put(
        module,
        importAndroidResult.androidResourceModules.stream()
            .filter(module -> module.targetKey.getLabel().targetName().toString().equals("app"))
            .findFirst()
            .orElse(null));
    registerProjectService(AndroidResourceModuleRegistry.class, registry);

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
            LibraryKey.libraryNameFromArtifactLocation(source("third_party/aar/lib_aar.aar")),
            LibraryKey.libraryNameFromArtifactLocation(source("third_party/guava-21.jar")));
  }
}
