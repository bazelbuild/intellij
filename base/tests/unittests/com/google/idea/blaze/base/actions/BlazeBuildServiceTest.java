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
package com.google.idea.blaze.base.actions;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.idea.blaze.base.BlazeTestCase;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.MockBlazeProjectDataBuilder;
import com.google.idea.blaze.base.model.MockBlazeProjectDataManager;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.TargetExpression;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.projectview.ProjectView;
import com.google.idea.blaze.base.projectview.ProjectViewManager;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.projectview.section.ListSection;
import com.google.idea.blaze.base.projectview.section.sections.TargetSection;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.BlazeImportSettings;
import com.google.idea.blaze.base.settings.BlazeImportSettingsManager;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.base.sync.workspace.WorkspacePathResolver;
import java.io.File;
import java.util.List;
import javax.annotation.Nullable;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Test cases for {@link BlazeBuildService}. */
@RunWith(JUnit4.class)
public class BlazeBuildServiceTest extends BlazeTestCase {
  private BlazeBuildService service;
  private ProjectViewSet viewSet;
  private final WorkspaceRoot workspaceRoot = new WorkspaceRoot(new File("/"));

  @Override
  protected void initTest(Container applicationServices, Container projectServices) {
    BlazeImportSettingsManager importSettingsManager = new BlazeImportSettingsManager();
    importSettingsManager.setImportSettings(
        new BlazeImportSettings("", "", "", "", Blaze.BuildSystem.Blaze));
    projectServices.register(BlazeImportSettingsManager.class, importSettingsManager);

    ProjectView view =
        ProjectView.builder()
            .add(
                ListSection.builder(TargetSection.KEY)
                    .add(TargetExpression.fromString("//view/target:one"))
                    .add(TargetExpression.fromString("//view/target:two")))
            .build();
    viewSet = ProjectViewSet.builder().add(new File("view/target/.blazeproject"), view).build();
    ProjectViewManager viewManager = new MockProjectViewManager(viewSet);
    projectServices.register(ProjectViewManager.class, viewManager);

    BlazeProjectData blazeProjectData = MockBlazeProjectDataBuilder.builder(workspaceRoot).build();
    projectServices.register(
        BlazeProjectDataManager.class, new MockBlazeProjectDataManager(blazeProjectData));

    applicationServices.register(BlazeBuildService.class, spy(new BlazeBuildService()));

    service = BlazeBuildService.getInstance();
    assertThat(service).isNotNull();

    // Can't mock BlazeExecutor.submitTask.
    doNothing().when(service).buildTargetExpressions(any(), any(), any(), any());
  }

  @Test
  public void testBuildFile() {
    ImmutableCollection<Label> labels =
        ImmutableList.of(Label.create("//foo:bar"), Label.create("//foo:baz"));
    List<TargetExpression> targets = Lists.newArrayList(labels);
    service.buildFile(project, "Foo.java", labels);
    verify(service).buildTargetExpressions(eq(project), eq(targets), eq(viewSet), any());
  }

  @Test
  public void testBuildProject() {
    service.buildProject(project);
    List<TargetExpression> targets =
        Lists.newArrayList(
            TargetExpression.fromString("//view/target:one"),
            TargetExpression.fromString("//view/target:two"));
    verify(service).buildTargetExpressions(eq(project), eq(targets), eq(viewSet), any());
  }

  private static class MockProjectViewManager extends ProjectViewManager {
    private final ProjectViewSet viewSet;

    public MockProjectViewManager(ProjectViewSet viewSet) {
      this.viewSet = viewSet;
    }

    @Nullable
    @Override
    public ProjectViewSet getProjectViewSet() {
      return viewSet;
    }

    @Nullable
    @Override
    public ProjectViewSet reloadProjectView(
        BlazeContext context, WorkspacePathResolver workspacePathResolver) {
      return viewSet;
    }
  }
}
