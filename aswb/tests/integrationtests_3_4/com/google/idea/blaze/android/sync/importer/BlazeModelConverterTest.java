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
package com.google.idea.blaze.android.sync.importer;

import static com.android.projectmodel.VariantUtil.ARTIFACT_NAME_MAIN;
import static com.google.common.truth.Truth.assertThat;
import static com.google.idea.blaze.android.sync.importer.BlazeImportInput.createLooksLikeAarLibrary;

import com.android.ide.common.util.PathString;
import com.android.projectmodel.AndroidModel;
import com.android.projectmodel.AndroidPathType;
import com.android.projectmodel.AndroidSubmodule;
import com.android.projectmodel.Artifact;
import com.android.projectmodel.ArtifactDependency;
import com.android.projectmodel.ArtifactDependencyUtil;
import com.android.projectmodel.Config;
import com.android.projectmodel.ExternalLibrary;
import com.android.projectmodel.Library;
import com.android.projectmodel.ProjectLibrary;
import com.android.projectmodel.ProjectType;
import com.android.projectmodel.SubmodulePath;
import com.android.sdklib.AndroidVersion;
import com.google.idea.blaze.android.AndroidIntegrationTestSetupRule;
import com.google.idea.blaze.android.sync.model.idea.BlazeImportFixture;
import com.google.idea.blaze.base.BlazeIntegrationTestCase;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.common.experiments.ExperimentService;
import com.google.idea.common.experiments.MockExperimentService;
import java.util.List;
import java.util.stream.Collectors;
import kotlin.sequences.SequencesKt;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Integration tests for {@link BlazeModuleConverter}. */
@RunWith(JUnit4.class)
public class BlazeModelConverterTest extends BlazeIntegrationTestCase {
  @Rule
  public final AndroidIntegrationTestSetupRule androidSetupRule =
      new AndroidIntegrationTestSetupRule();

  private BlazeContext context;
  private BlazeImportFixture importFixture;
  private BlazeModelConverter converter;

  @Before
  public void doSetup() {
    context = new BlazeContext();
    MockExperimentService experimentService = new MockExperimentService();
    registerApplicationComponent(ExperimentService.class, experimentService);
    experimentService.setExperiment(createLooksLikeAarLibrary, true);

    importFixture = new BlazeImportFixture(getProject(), fileSystem, workspaceRoot, context);
    converter =
        new BlazeModelConverter(
            importFixture.getProject(),
            importFixture.getWorkspaceRoot(),
            importFixture.getProjectViewSet(),
            importFixture.getBlazeProjectData());
  }

  @Test
  public void testCreateWorkspaceModel() {
    AndroidModel model = converter.createWorkspaceModuleModel();
    assertThat(model).isNotNull();

    List<String> submoduleNames =
        model.getSubmodules().stream().map(AndroidSubmodule::getName).collect(Collectors.toList());

    assertThat(submoduleNames)
        .containsExactly("//java/com/google:app", "//java/com/google/intermediate:intermediate");

    AndroidSubmodule submodule = model.getSubmodule("//java/com/google:app");
    assertThat(submodule.getType()).isEqualTo(ProjectType.APP);

    List<SubmodulePath> variants =
        SequencesKt.toList(submodule.getConfigTable().getSchema().allVariantPaths());
    assertThat(variants.size()).isEqualTo(1);
    SubmodulePath variantPath = variants.get(0);
    SubmodulePath artifactPath = variantPath.plus(ARTIFACT_NAME_MAIN);
    Artifact mainArtifact = submodule.getArtifact(artifactPath);
    Config resolvedConfig = mainArtifact.getResolved();
    assertThat(resolvedConfig.getManifestValues().getApiVersion())
        .isEqualTo(new AndroidVersion(15, "stable"));

    List<Library> dependencies =
        SequencesKt.toList(ArtifactDependencyUtil.visitEach(resolvedConfig.getCompileDeps()))
            .stream()
            .map(ArtifactDependency::getLibrary)
            .collect(Collectors.toList());

    List<String> projectLibAddresses =
        dependencies.stream()
            .filter(it -> it instanceof ProjectLibrary)
            .map(Library::getAddress)
            .collect(Collectors.toList());
    assertThat(projectLibAddresses).containsExactly("//java/com/google/intermediate:intermediate");

    List<String> externalLibAddresses =
        dependencies.stream()
            .filter(it -> it instanceof ExternalLibrary)
            .map(Library::getAddress)
            .collect(Collectors.toList());

    assertThat(externalLibAddresses)
        .containsExactly(
            "file:///src/workspace/third_party/shared/res",
            "//third_party/guava:java",
            "//third_party/quantum:values",
            "file:///src/workspace/third_party/quantum/res",
            "//third_party/aar:an_aar");

    // Assert that RES source folders are imported correctly
    AndroidSubmodule librarySubmodule =
        model.getSubmodule("//java/com/google/intermediate:intermediate");
    Artifact libraryArtifact = librarySubmodule.getArtifact(artifactPath);
    List<PathString> resourceFolders =
        libraryArtifact.getResolved().getSources().get(AndroidPathType.RES);
    assertThat(resourceFolders)
        .containsExactly(new PathString("/src/workspace/java/com/google/intermediate/res"));
  }
}
