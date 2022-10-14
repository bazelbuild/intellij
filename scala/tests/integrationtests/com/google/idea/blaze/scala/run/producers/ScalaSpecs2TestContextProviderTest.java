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

/** Integration tests for {@link ScalaSpecs2TestContextProvider}. */
@RunWith(JUnit4.class)
public class ScalaSpecs2TestContextProviderTest extends BlazeRunConfigurationProducerTestCase {

  @Test
  public void testSpecs2TestProducedFromPsiClass() throws Throwable {
    PsiFile file = createTestPsiFile();

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
    // TODO: add tests for infix expression run configurations
    // TODO: also test BlazeScalaTestEventsHandler
  }

  private PsiFile createTestPsiFile() throws Throwable {
    createAndIndexFile(
        WorkspacePath.createIfValid("scala/org/junit/Test.scala"),
        "package org.junit",
        "class Test");
    createAndIndexFile(
        WorkspacePath.createIfValid("scala/org/junit/runner/RunWith.scala"),
        "package org.junit.runner",
        "class RunWith");
    createAndIndexFile(
        WorkspacePath.createIfValid("scala/org/specs2/runner/JUnitRunner.scala"),
        "package org.specs2.runner",
        "class JUnitRunner");
    createAndIndexFile(
        WorkspacePath.createIfValid("scala/org/specs2/mutable/SpecificationWithJUnit.scala"),
        "package org.specs2.mutable",
        "@org.junit.runner.RunWith(classOf[org.specs2.runner.JUnitRunner])",
        "abstract class SpecificationWithJUnit extends org.specs2.mutable.Specification");
    createAndIndexFile(
        WorkspacePath.createIfValid("scala/org/specs2/mutable/Specification.scala"),
        "package org.specs2.mutable",
        "abstract class Specification extends org.specs2.mutable.SpecificationLike");
    createAndIndexFile(
        WorkspacePath.createIfValid("scala/org/specs2/mutable/SpecificationLike.scala"),
        "package org.specs2.mutable",
        "trait SpecificationLike extends",
        "org.specs2.specification.core.mutable.SpecificationStructure");
    createAndIndexFile(
        WorkspacePath.createIfValid(
            "scala/org/specs2/specification/core/mutable/SpecificationStructure.scala"),
        "package org.specs2.specification.core.mutable",
        "trait SpecificationStructure extends",
        "org.specs2.specification.core.SpecificationStructure");
    createAndIndexFile(
        WorkspacePath.createIfValid(
            "scala/org/specs2/specification/core/SpecificationStructure.scala"),
        "package org.specs2.specification.core",
        "trait SpecificationStructure");
    createAndIndexFile(
        WorkspacePath.createIfValid("scala/org/specs2/specification/core/Fragment.scala"),
        "package org.specs2.specification.core",
        "class Fragment");
    // TODO: figure out why "should" and "in" don't resolve
    return createAndIndexFile(
        WorkspacePath.createIfValid("scala/com/google/test/TestClass.scala"),
        "package com.google.test",
        "class TestClass extends org.specs2.mutable.SpecificationWithJUnit {",
        "  implicit class String(s: java.lang.String) {",
        "    def should(f: org.specs2.specification.core.Fragment) = f",
        "    def in[R](r: R) = null",
        "  }",
        "  \"foo\" should { \"do bar\" in {} }",
        "}");
  }
}
