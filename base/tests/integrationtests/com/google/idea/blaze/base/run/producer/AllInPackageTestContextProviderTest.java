/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.run.producer;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.base.Joiner;
import com.google.idea.blaze.base.MockProjectViewManager;
import com.google.idea.blaze.base.command.BlazeCommandName;
import com.google.idea.blaze.base.model.primitives.TargetExpression;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.projectview.parser.ProjectViewParser;
import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration;
import com.google.idea.blaze.base.run.producers.TestContextRunConfigurationProducer;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.ErrorCollector;
import com.google.idea.blaze.base.scope.output.IssueOutput;
import com.google.idea.blaze.base.sync.workspace.WorkspacePathResolverImpl;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.actions.ConfigurationFromContext;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Integration tests for {@link AllInPackageTestContextProvider}. */
@RunWith(JUnit4.class)
public class AllInPackageTestContextProviderTest extends BlazeRunConfigurationProducerTestCase {

  private ErrorCollector errorCollector;
  private BlazeContext context;
  private MockProjectViewManager projectViewManager;

  @Before
  public final void before() {
    projectViewManager = new MockProjectViewManager(getProject());
    errorCollector = new ErrorCollector();
    context = new BlazeContext();
    context.addOutputSink(IssueOutput.class, errorCollector);
  }

  protected void setProjectView(String... contents) {
    ProjectViewParser projectViewParser =
        new ProjectViewParser(context, new WorkspacePathResolverImpl(workspaceRoot));
    projectViewParser.parseProjectView(Joiner.on("\n").join(contents));

    ProjectViewSet result = projectViewParser.getResult();
    assertThat(result.getProjectViewFiles()).isNotEmpty();
    errorCollector.assertNoIssues();
    projectViewManager.setProjectView(result);
  }

  @Test
  public void testProducedFromBuildFile() {
    setProjectView(
        "directories:", "  java/com/google/test", "targets:", "  //java/com/google/test:lib");

    PsiFile buildFile =
        workspace.createPsiFile(
            new WorkspacePath("java/com/google/test/BUILD"), "java_test(name='unit_tests'");

    ConfigurationContext context = createContextFromPsi(buildFile);
    List<ConfigurationFromContext> configurations = context.getConfigurationsFromContext();
    assertThat(configurations).hasSize(1);

    ConfigurationFromContext fromContext = configurations.get(0);
    assertThat(fromContext.isProducedBy(TestContextRunConfigurationProducer.class)).isTrue();
    assertThat(fromContext.getConfiguration()).isInstanceOf(BlazeCommandRunConfiguration.class);

    BlazeCommandRunConfiguration config =
        (BlazeCommandRunConfiguration) fromContext.getConfiguration();
    assertThat(config.getTargets())
        .containsExactly(TargetExpression.fromStringSafe("//java/com/google/test:all"));
    assertThat(getCommandType(config)).isEqualTo(BlazeCommandName.TEST);
  }

  @Test
  public void testProducedFromBlazePackage() {
    setProjectView("directories:", "  java/com/google/test");
    PsiDirectory directory =
        workspace
            .createPsiFile(
                new WorkspacePath("java/com/google/test/BUILD"), "java_test(name='unit_tests'")
            .getParent();

    ConfigurationContext context = createContextFromPsi(directory);

    TestContextRunConfigurationProducer producer = new TestContextRunConfigurationProducer();
    BlazeCommandRunConfiguration config =
        (BlazeCommandRunConfiguration)
            producer.createConfigurationFromContext(context).getConfiguration();

    assertThat(config.getTargets())
        .containsExactly(TargetExpression.fromStringSafe("//java/com/google/test/...:all"));
    assertThat(getCommandType(config)).isEqualTo(BlazeCommandName.TEST);
  }

  @Test
  public void testProducedFromNonBlazePackage() {
    setProjectView("directories:", "  java/com/google/test");
    PsiDirectory directory =
        workspace.createPsiDirectory(new WorkspacePath("java/com/google/test"));

    ConfigurationContext context = createContextFromPsi(directory);

    TestContextRunConfigurationProducer producer = new TestContextRunConfigurationProducer();
    BlazeCommandRunConfiguration config =
        (BlazeCommandRunConfiguration)
            producer.createConfigurationFromContext(context).getConfiguration();

    assertThat(config.getTargets())
        .containsExactly(TargetExpression.fromStringSafe("//java/com/google/test/...:all"));
    assertThat(getCommandType(config)).isEqualTo(BlazeCommandName.TEST);
  }

  @Test
  public void testProducedFromNonBuildFile() {
    setProjectView(
        "directories:", "  java/com/google/test", "targets:", "  //java/com/google/test:lib");

    PsiFile buildFile =
        workspace.createPsiFile(
            new WorkspacePath("java/com/google/test/whatever"), "I am just a random file!");

    List<ConfigurationFromContext> configurations =
        createContextFromPsi(buildFile).getConfigurationsFromContext();

    assertThat(configurations).isNull();
  }
}
