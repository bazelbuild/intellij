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
package com.google.idea.blaze.scala.run.producers;

import static com.google.common.truth.Truth.assertThat;

import com.google.idea.blaze.base.command.BlazeCommandName;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetMapBuilder;
import com.google.idea.blaze.base.model.MockBlazeProjectDataBuilder;
import com.google.idea.blaze.base.model.MockBlazeProjectDataManager;
import com.google.idea.blaze.base.model.primitives.TargetExpression;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration;
import com.google.idea.blaze.base.run.producers.BlazeRunConfigurationProducerTestCase;
import com.google.idea.blaze.base.run.producers.TestContextRunConfigurationProducer;
import com.google.idea.blaze.base.run.state.BlazeCommandRunConfigurationCommonState;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.actions.ConfigurationFromContext;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;

import java.util.List;
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Integration tests for {@link ScalaTestContextProvider} and {@link JavaTestContextProvider}. */
@RunWith(JUnit4.class)
public class BlazeScalaTestClassConfigurationProducerTest
    extends BlazeRunConfigurationProducerTestCase {

  @Test
  public void testJunitTestProducedFromPsiClass() throws Throwable {
    PsiFile file =
        createAndIndexFile(
            new WorkspacePath("scala/com/google/test/TestClass.scala"),
            "package com.google.test {",
            "  class TestClass {",
            "    @org.junit.Test",
            "    def testMethod() {}",
            "  }",
            "}",
            "package org.junit { trait Test }");
    assertThat(file).isInstanceOf(ScalaFile.class);
    ScalaFile scalaFile = (ScalaFile) file;
    PsiClass[] classes = scalaFile.getClasses();
    assertThat(classes).isNotEmpty();
    PsiClass testClass = classes[0];

    MockBlazeProjectDataBuilder builder = MockBlazeProjectDataBuilder.builder(workspaceRoot);
    builder.setTargetMap(
        TargetMapBuilder.builder()
            .addTarget(
                TargetIdeInfo.builder()
                    .setKind("scala_junit_test")
                    .setLabel("//scala/com/google/test:TestClass")
                    .addSource(sourceRoot("scala/com/google/test/TestClass.scala"))
                    .build())
            .build());
    registerProjectService(
        BlazeProjectDataManager.class, new MockBlazeProjectDataManager(builder.build()));

    ConfigurationContext context = createContextFromPsi(testClass);
    List<ConfigurationFromContext> configurations = context.getConfigurationsFromContext();
    assertThat(configurations).isNotNull();
    assertThat(configurations).hasSize(1);
    ConfigurationFromContext fromContext = configurations.get(0);
    assertThat(fromContext.isProducedBy(TestContextRunConfigurationProducer.class)).isTrue();
    assertThat(fromContext.getConfiguration()).isInstanceOf(BlazeCommandRunConfiguration.class);

    BlazeCommandRunConfiguration config =
        (BlazeCommandRunConfiguration) fromContext.getConfiguration();
    assertThat(config.getTargets())
        .containsExactly(TargetExpression.fromStringSafe("//scala/com/google/test:TestClass"));
    assertThat(getTestFilterContents(config)).isEqualTo("--test_filter=com.google.test.TestClass#");
    assertThat(config.getName()).isEqualTo("Bazel test TestClass");
    assertThat(getCommandType(config)).isEqualTo(BlazeCommandName.TEST);
  }

  @Test
  public void testScalaTestProducedFromPsiClass() throws Throwable {
    PsiFile file =
        createAndIndexFile(
            WorkspacePath.createIfValid("scala/com/google/test/TestClass.scala"),
            "package com.google.test {",
            "  class TestClass extends org.scalatest.FlatSpec {",
            "    \"this test\" should \"pass\" in {}",
            "  }",
            "}",
            "package org.scalatest {",
            "  trait FlatSpec extends Suite",
            "  trait Suite",
            "}");
    assertThat(file).isInstanceOf(ScalaFile.class);
    ScalaFile scalaFile = (ScalaFile) file;
    PsiClass[] classes = scalaFile.getClasses();
    assertThat(classes).isNotEmpty();
    PsiClass testClass = classes[0];

    MockBlazeProjectDataBuilder builder = MockBlazeProjectDataBuilder.builder(workspaceRoot);
    builder.setTargetMap(
        TargetMapBuilder.builder()
            .addTarget(
                TargetIdeInfo.builder()
                    .setKind("scala_test")
                    .setLabel("//scala/com/google/test:TestClass")
                    .addSource(sourceRoot("scala/com/google/test/TestClass.scala"))
                    .build())
            .build());
    registerProjectService(
        BlazeProjectDataManager.class, new MockBlazeProjectDataManager(builder.build()));

    ConfigurationContext context = createContextFromPsi(testClass);
    List<ConfigurationFromContext> configurations = context.getConfigurationsFromContext();
    assertThat(configurations).isNotNull();
    assertThat(configurations).hasSize(1);
    ConfigurationFromContext fromContext = configurations.get(0);
    assertThat(fromContext.isProducedBy(TestContextRunConfigurationProducer.class)).isTrue();
    assertThat(fromContext.getConfiguration()).isInstanceOf(BlazeCommandRunConfiguration.class);

    BlazeCommandRunConfiguration config =
        (BlazeCommandRunConfiguration) fromContext.getConfiguration();
    assertThat(config.getTargets())
        .containsExactly(TargetExpression.fromStringSafe("//scala/com/google/test:TestClass"));
    BlazeCommandRunConfigurationCommonState handlerState =
        config.getHandlerStateIfType(BlazeCommandRunConfigurationCommonState.class);
    assertThat(handlerState).isNotNull();
    List<String> testArgs = handlerState.getTestArgs();
    assertThat(testArgs).containsExactly("-s", "com.google.test.TestClass").inOrder();
    assertThat(config.getName()).isEqualTo("Bazel test TestClass");
    assertThat(getCommandType(config)).isEqualTo(BlazeCommandName.TEST);
  }
}
