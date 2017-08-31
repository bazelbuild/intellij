/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.golang.resolve;

import static com.google.common.truth.Truth.assertThat;

import com.goide.psi.GoFile;
import com.goide.psi.GoImportSpec;
import com.goide.psi.GoTypeReferenceExpression;
import com.goide.psi.GoTypeSpec;
import com.google.idea.blaze.base.BlazeIntegrationTestCase;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetMapBuilder;
import com.google.idea.blaze.base.lang.buildfile.psi.util.PsiUtils;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.MockBlazeProjectDataBuilder;
import com.google.idea.blaze.base.model.MockBlazeProjectDataManager;
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.plugin.PluginUtils;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.golang.BlazeGoSupport;
import com.google.idea.common.experiments.ExperimentService;
import com.google.idea.common.experiments.MockExperimentService;
import java.util.Arrays;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Integration tests for {@link BlazeGoImportResolver}. */
@RunWith(JUnit4.class)
public class BlazeGoImportResolverTest extends BlazeIntegrationTestCase {

  @Before
  public void init() {
    MockExperimentService experimentService = new MockExperimentService();
    experimentService.setExperiment(BlazeGoSupport.blazeGoSupportEnabled, true);
    registerApplicationComponent(ExperimentService.class, experimentService);
  }

  @Test
  public void testGoPluginEnabled() {
    assertThat(PluginUtils.isPluginEnabled("org.jetbrains.plugins.go")).isTrue();
  }

  @Test
  public void testGoLibraryPackageResolves() {
    setProjectTargets(
        TargetIdeInfo.builder()
            .setKind(Kind.GO_LIBRARY)
            .setLabel("//package/path/foo:bar")
            .setBuildFile(sourceRoot("package/path/foo/BUILD"))
            .build());
    GoFile barFile =
        (GoFile)
            workspace.createPsiFile(
                new WorkspacePath("package/path/foo/baz.go"),
                "package bar",
                "type Struct struct {}");
    GoFile referencingFile =
        (GoFile)
            workspace.createPsiFile(
                new WorkspacePath("different/package/path/gofile.go"),
                "package bar",
                "import \"workspace/package/path/foo/bar\"",
                "",
                "type Context interface {",
                "  Method() (x bar.Struct)",
                "}");

    GoImportSpec importSpec =
        PsiUtils.findFirstChildOfClassRecursive(referencingFile, GoImportSpec.class);
    assertThat(importSpec).isNotNull();
    assertThat(importSpec.resolve()).isEqualTo(barFile.getParent());

    GoTypeReferenceExpression typeReference =
        PsiUtils.findLastChildOfClassRecursive(referencingFile, GoTypeReferenceExpression.class);
    GoTypeSpec typeSpec = PsiUtils.findFirstChildOfClassRecursive(barFile, GoTypeSpec.class);
    assertThat(typeReference).isNotNull();
    assertThat(typeReference.resolve()).isEqualTo(typeSpec);
  }

  private void setProjectTargets(TargetIdeInfo... targets) {
    TargetMapBuilder targetMap = TargetMapBuilder.builder();
    Arrays.stream(targets).forEach(targetMap::addTarget);
    BlazeProjectData projectData =
        MockBlazeProjectDataBuilder.builder(workspaceRoot).setTargetMap(targetMap.build()).build();
    registerProjectService(
        BlazeProjectDataManager.class, new MockBlazeProjectDataManager(projectData));
  }

  private static ArtifactLocation sourceRoot(String relativePath) {
    return ArtifactLocation.builder().setRelativePath(relativePath).setIsSource(true).build();
  }
}
