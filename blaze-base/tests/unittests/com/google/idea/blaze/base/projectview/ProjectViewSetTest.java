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
package com.google.idea.blaze.base.projectview;

import com.google.idea.blaze.base.BlazeTestCase;
import com.google.idea.blaze.base.TestUtils;
import com.google.idea.blaze.base.experiments.ExperimentService;
import com.google.idea.blaze.base.experiments.MockExperimentService;
import com.google.idea.blaze.base.model.primitives.*;
import com.google.idea.blaze.base.projectview.section.*;
import com.google.idea.blaze.base.projectview.section.sections.*;
import com.google.idea.blaze.base.sync.BlazeSyncPlugin;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import static com.google.common.truth.Truth.assertThat;

public class ProjectViewSetTest extends BlazeTestCase {

  @Override
  protected void initTest(@NotNull Container applicationServices, @NotNull Container projectServices) {
    super.initTest(applicationServices, projectServices);
    applicationServices.register(ExperimentService.class, new MockExperimentService());
    registerExtensionPoint(BlazeSyncPlugin.EP_NAME, BlazeSyncPlugin.class);
  }

  @Test
  public void testProjectViewSetSerializable() throws Exception {
    ProjectViewSet projectViewSet = ProjectViewSet.builder()
      .add(ProjectView.builder()
             .put(ListSection.builder(DirectorySection.KEY).add(DirectoryEntry.include(new WorkspacePath("test"))))
             .put(ListSection.builder(TargetSection.KEY).add(TargetExpression.fromString("//test:all")))
             .put(ScalarSection.builder(ImportSection.KEY).set(new WorkspacePath("test")))
             .put(ListSection.builder(TestSourceSection.KEY).add(new Glob("javatests/*")))
             .put(ListSection.builder(ExcludedSourceSection.KEY).add(new Glob("*.java")))
             .put(ListSection.builder(BuildFlagsSection.KEY).add("--android_sdk=abcd"))
             .put(ListSection.builder(ImportTargetOutputSection.KEY).add(new Label("//test:test")))
             .put(ListSection.builder(ExcludeTargetSection.KEY).add(new Label("//test:test")))
             .put(ScalarSection.builder(WorkspaceTypeSection.KEY).set(WorkspaceType.JAVA))
             .put(ListSection.builder(AdditionalLanguagesSection.KEY).add(LanguageClass.JAVA))
             .put(ScalarSection.builder(MetricsProjectSection.KEY).set("my project"))
             .build())
      .build();

    // Assert we have all sections
    assert projectViewSet.getTopLevelProjectViewFile() != null;
    ProjectView projectView = projectViewSet.getTopLevelProjectViewFile().projectView;
    for (SectionParser parser : Sections.getParsers()) {
      Section section = projectView.getSectionOfType(parser.getSectionKey());
      assertThat(section).isNotNull();
    }

    TestUtils.assertIsSerializable(projectViewSet);
  }
}
