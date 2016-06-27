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
package com.google.idea.blaze.base.sync;

import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.base.BlazeTestCase;
import com.google.idea.blaze.base.sync.projectview.LanguageSupport;
import com.google.idea.blaze.base.sync.projectview.WorkspaceLanguageSettings;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.model.primitives.WorkspaceType;
import com.google.idea.blaze.base.projectview.ProjectView;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.projectview.section.ScalarSection;
import com.google.idea.blaze.base.projectview.section.sections.WorkspaceTypeSection;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.ErrorCollector;
import com.google.idea.blaze.base.scope.output.IssueOutput;
import com.intellij.openapi.extensions.impl.ExtensionPointImpl;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import java.util.Set;

import static com.google.common.truth.Truth.assertThat;

/**
 * Test cases for {@link LanguageSupport}
 */
public class LanguageSupportTest extends BlazeTestCase {
  private ErrorCollector errorCollector = new ErrorCollector();
  private BlazeContext context;
  private ExtensionPointImpl<BlazeSyncPlugin> syncPlugins;

  @Override
  protected void initTest(@NotNull Container applicationServices, @NotNull Container projectServices) {
    super.initTest(applicationServices, projectServices);

    syncPlugins = registerExtensionPoint(BlazeSyncPlugin.EP_NAME, BlazeSyncPlugin.class);

    context = new BlazeContext();
    context.addOutputSink(IssueOutput.class, errorCollector);
  }

  @Test
  public void testSimpleCase() {
    syncPlugins.registerExtension(new BlazeSyncPlugin.Adapter() {
      @Override
      public Set<LanguageClass> getSupportedLanguagesInWorkspace(WorkspaceType workspaceType) {
        return ImmutableSet.of(LanguageClass.C);
      }
    });

    ProjectViewSet projectViewSet = ProjectViewSet.builder()
      .add(ProjectView.builder()
             .put(ScalarSection.builder(WorkspaceTypeSection.KEY)
                    .set(WorkspaceType.C))
             .build())
      .build();
    WorkspaceLanguageSettings workspaceLanguageSettings = LanguageSupport.createWorkspaceLanguageSettings(context, projectViewSet);
    errorCollector.assertNoIssues();
    assertThat(workspaceLanguageSettings).isEqualTo(
      new WorkspaceLanguageSettings(WorkspaceType.C, ImmutableSet.of(LanguageClass.C))
    );
  }

  @Test
  public void testFailWithUnsupportedLanguage() {
    ProjectViewSet projectViewSet = ProjectViewSet.builder()
      .add(ProjectView.builder()
             .put(ScalarSection.builder(WorkspaceTypeSection.KEY)
                    .set(WorkspaceType.C))
             .build())
      .build();
    LanguageSupport.createWorkspaceLanguageSettings(context, projectViewSet);
    errorCollector.assertIssues(
      "Language 'c' is not supported for this plugin with workspace type: 'c'");
  }

  /**
   * Tests that we ask for java and android when the workspace type is android.
   */
  @Test
  public void testWorkspaceTypeImpliesLanguages() {
    syncPlugins.registerExtension(new BlazeSyncPlugin.Adapter() {
      @Override
      public Set<LanguageClass> getSupportedLanguagesInWorkspace(WorkspaceType workspaceType) {
        return ImmutableSet.of(LanguageClass.ANDROID, LanguageClass.JAVA, LanguageClass.C);
      }
    });

    ProjectViewSet projectViewSet = ProjectViewSet.builder()
      .add(ProjectView.builder()
             .put(ScalarSection.builder(WorkspaceTypeSection.KEY)
                    .set(WorkspaceType.ANDROID))
             .build())
      .build();
    WorkspaceLanguageSettings workspaceLanguageSettings = LanguageSupport.createWorkspaceLanguageSettings(context, projectViewSet);
    assertThat(workspaceLanguageSettings).isEqualTo(
      new WorkspaceLanguageSettings(WorkspaceType.ANDROID, ImmutableSet.of(LanguageClass.JAVA, LanguageClass.ANDROID))
    );
  }
}
