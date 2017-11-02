package com.google.idea.blaze.scala.run.producers;

import com.google.idea.blaze.base.command.BlazeCommandName;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetMapBuilder;
import com.google.idea.blaze.base.model.MockBlazeProjectDataBuilder;
import com.google.idea.blaze.base.model.MockBlazeProjectDataManager;
import com.google.idea.blaze.base.model.primitives.TargetExpression;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration;
import com.google.idea.blaze.base.run.producer.BlazeRunConfigurationProducerTestCase;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.actions.ConfigurationFromContext;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.List;

import static com.google.common.truth.Truth.assertThat;

/**
 * Integration tests for {@link BlazeSpecs2ConfigurationProducer}.
 */
@RunWith(JUnit4.class)
public class BlazeSpecs2ConfigurationProducerTest
    extends BlazeRunConfigurationProducerTestCase {

    @Test
    public void testSpecs2TestProducedFromPsiClass() {
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
        assertThat(fromContext.isProducedBy(BlazeSpecs2ConfigurationProducer.class)).isTrue();
        assertThat(fromContext.getConfiguration()).isInstanceOf(BlazeCommandRunConfiguration.class);

        BlazeCommandRunConfiguration config =
                (BlazeCommandRunConfiguration) fromContext.getConfiguration();
        assertThat(config.getTarget())
                .isEqualTo(TargetExpression.fromStringSafe("//scala/com/google/test:TestClass"));
        assertThat(getTestFilterContents(config)).isEqualTo("--test_filter=com.google.test.TestClass#");
        assertThat(config.getName()).isEqualTo("TestClass");
        assertThat(getCommandType(config)).isEqualTo(BlazeCommandName.TEST);
    }

    private PsiFile createTestPsiFile() {
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
        return createAndIndexFile(
                WorkspacePath.createIfValid("scala/com/google/test/TestClass.scala"),
                "package com.google.test",
                "class TestClass extends org.specs2.mutable.SpecificationWithJUnit");
    }

}
