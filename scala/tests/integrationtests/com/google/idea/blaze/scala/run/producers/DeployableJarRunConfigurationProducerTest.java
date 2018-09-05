package com.google.idea.blaze.scala.run.producers;

import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetMapBuilder;
import com.google.idea.blaze.base.model.MockBlazeProjectDataBuilder;
import com.google.idea.blaze.base.model.MockBlazeProjectDataManager;
import com.google.idea.blaze.base.model.primitives.TargetExpression;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.run.producer.BlazeRunConfigurationProducerTestCase;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.intellij.execution.application.ApplicationConfiguration;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.psi.PsiFile;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import static com.google.common.truth.Truth.assertThat;

/** Integration tests for {@link DeployableJarRunConfigurationProducerTest}. */
@RunWith(JUnit4.class)
public class DeployableJarRunConfigurationProducerTest
    extends BlazeRunConfigurationProducerTestCase {

    @Test
    public void testCorrectMainAppAndTargetChosen() {
        MockBlazeProjectDataBuilder builder = MockBlazeProjectDataBuilder.builder(workspaceRoot);
        builder.setTargetMap(
            TargetMapBuilder.builder()
                .addTarget(
                    TargetIdeInfo.builder()
                        .setKind("scala_library")
                        .setLabel("//com/google/library:SomeLibrary")
                        .addSource(sourceRoot("com/google/library/SomeLibrary.scala"))
                        .build())
                    .build());
        registerProjectService(
                BlazeProjectDataManager.class, new MockBlazeProjectDataManager(builder.build()));

        PsiFile scalaFile =
            createAndIndexFile(
                WorkspacePath.createIfValid("com/google/library/SomeLibrary.scala"),
                "package com.google.library {",
                "  object Foo {",
                "    def main(args: Array[String]) {}",
                "  }",
                "}",
                "package scala { final class Array[T] {} }",
                "package java.lang { public final class String {} }");

        RunConfiguration config = createConfigurationFromLocation(scalaFile);

        assertThat(config).isInstanceOf(ApplicationConfiguration.class);
        ApplicationConfiguration appConfig = (ApplicationConfiguration) config;
        assertThat(appConfig).isNotNull();
        assertThat(appConfig.getMainClass().getQualifiedName())
                .isEqualTo("com.google.library.Foo");
        assertThat(appConfig.getUserData(DeployableJarRunConfigurationProducer.TARGET_LABEL)).isNotNull();
        assertThat(appConfig.getUserData(DeployableJarRunConfigurationProducer.TARGET_LABEL))
                .isEqualTo(TargetExpression.fromStringSafe("//com/google/library:SomeLibrary"));
    }

}