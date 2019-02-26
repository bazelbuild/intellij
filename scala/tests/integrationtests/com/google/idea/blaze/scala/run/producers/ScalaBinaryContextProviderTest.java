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

import com.google.idea.blaze.base.ideinfo.JavaIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetMapBuilder;
import com.google.idea.blaze.base.model.MockBlazeProjectDataBuilder;
import com.google.idea.blaze.base.model.MockBlazeProjectDataManager;
import com.google.idea.blaze.base.model.primitives.TargetExpression;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.run.BlazeRunConfiguration;
import com.google.idea.blaze.base.run.producer.BlazeRunConfigurationProducerTestCase;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.psi.PsiFile;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Integration tests for {@link ScalaBinaryContextProvider}. */
@RunWith(JUnit4.class)
public class ScalaBinaryContextProviderTest extends BlazeRunConfigurationProducerTestCase {

  @Test
  public void testUniqueScalaBinaryChosen() {
    MockBlazeProjectDataBuilder builder = MockBlazeProjectDataBuilder.builder(workspaceRoot);
    builder.setTargetMap(
        TargetMapBuilder.builder()
            .addTarget(
                TargetIdeInfo.builder()
                    .setKind("scala_binary")
                    .setLabel("//com/google/binary:UnrelatedName")
                    .addSource(sourceRoot("com/google/binary/MainClass.scala"))
                    .build())
            .build());
    registerProjectService(
        BlazeProjectDataManager.class, new MockBlazeProjectDataManager(builder.build()));

    PsiFile scalaFile =
        createAndIndexFile(
            WorkspacePath.createIfValid("com/google/binary/MainClass.scala"),
            "package com.google.binary {",
            "  object MainClass {",
            "    def main(args: Array[String]) {}",
            "  }",
            "}",
            "package scala { final class Array[T] {} }",
            "package java.lang { public final class String {} }");

    RunConfiguration config = createConfigurationFromLocation(scalaFile);

    assertThat(config).isInstanceOf(BlazeRunConfiguration.class);
    BlazeRunConfiguration blazeConfig = (BlazeRunConfiguration) config;
    assertThat(blazeConfig).isNotNull();
    assertThat(blazeConfig.getTarget())
        .isEqualTo(TargetExpression.fromStringSafe("//com/google/binary:UnrelatedName"));
  }

  @Test
  public void testNoScalaBinaryChosenIfNotInRDeps() {
    MockBlazeProjectDataBuilder builder = MockBlazeProjectDataBuilder.builder(workspaceRoot);
    builder.setTargetMap(
        TargetMapBuilder.builder()
            .addTarget(
                TargetIdeInfo.builder()
                    .setKind("scala_binary")
                    .setLabel("//com/google/binary:MainClass")
                    .addSource(sourceRoot("com/google/binary/OtherClass.scala"))
                    .build())
            .build());
    registerProjectService(
        BlazeProjectDataManager.class, new MockBlazeProjectDataManager(builder.build()));

    PsiFile scalaFile =
        createAndIndexFile(
            WorkspacePath.createIfValid("com/google/binary/MainClass.scala"),
            "package com.google.binary {",
            "  object MainClass {",
            "    def main(args: Array[String]) {}",
            "  }",
            "}",
            "package scala { final class Array[T] {} }",
            "package java.lang { public final class String {} }");

    assertThat(createConfigurationFromLocation(scalaFile))
        .isNotInstanceOf(BlazeRunConfiguration.class);
  }

  @Test
  public void testNoResultForObjectWithoutMainMethod() {
    MockBlazeProjectDataBuilder builder = MockBlazeProjectDataBuilder.builder(workspaceRoot);
    builder.setTargetMap(
        TargetMapBuilder.builder()
            .addTarget(
                TargetIdeInfo.builder()
                    .setKind("scala_binary")
                    .setLabel("//com/google/binary:MainClass")
                    .addSource(sourceRoot("com/google/binary/MainClass.scala"))
                    .setJavaInfo(JavaIdeInfo.builder().setMainClass("com.google.binary.MainClass"))
                    .build())
            .build());
    registerProjectService(
        BlazeProjectDataManager.class, new MockBlazeProjectDataManager(builder.build()));

    PsiFile scalaFile =
        createAndIndexFile(
            WorkspacePath.createIfValid("com/google/binary/MainClass.scala"),
            "package com.google.binary { object MainClass {} }",
            "package scala { final class Array[T] {} }",
            "package java.lang { public final class String {} }");

    assertThat(createConfigurationFromLocation(scalaFile)).isNull();
  }

  @Test
  public void testScalaBinaryWithMatchingNameChosen() {
    MockBlazeProjectDataBuilder builder = MockBlazeProjectDataBuilder.builder(workspaceRoot);
    builder.setTargetMap(
        TargetMapBuilder.builder()
            .addTarget(
                TargetIdeInfo.builder()
                    .setKind("scala_binary")
                    .setLabel("//com/google/binary:UnrelatedName")
                    .addSource(sourceRoot("com/google/binary/MainClass.scala"))
                    .build())
            .addTarget(
                TargetIdeInfo.builder()
                    .setKind("scala_binary")
                    .setLabel("//com/google/binary:MainClass")
                    .addSource(sourceRoot("com/google/binary/MainClass.scala"))
                    .build())
            .build());
    registerProjectService(
        BlazeProjectDataManager.class, new MockBlazeProjectDataManager(builder.build()));

    PsiFile scalaFile =
        createAndIndexFile(
            WorkspacePath.createIfValid("com/google/binary/MainClass.scala"),
            "package com.google.binary {",
            "  object MainClass {",
            "    def main(args: Array[String]) {}",
            "  }",
            "}",
            "package scala { final class Array[T] {} }",
            "package java.lang { public final class String {} }");

    RunConfiguration config = createConfigurationFromLocation(scalaFile);
    assertThat(config).isInstanceOf(BlazeRunConfiguration.class);
    BlazeRunConfiguration blazeConfig = (BlazeRunConfiguration) config;
    assertThat(blazeConfig).isNotNull();
    assertThat(blazeConfig.getTarget())
        .isEqualTo(TargetExpression.fromStringSafe("//com/google/binary:MainClass"));
  }

  @Test
  public void testScalaBinaryWithMatchingMainClassChosen() {
    MockBlazeProjectDataBuilder builder = MockBlazeProjectDataBuilder.builder(workspaceRoot);
    builder.setTargetMap(
        TargetMapBuilder.builder()
            .addTarget(
                TargetIdeInfo.builder()
                    .setKind("scala_binary")
                    .setLabel("//com/google/binary:UnrelatedName")
                    .addSource(sourceRoot("com/google/binary/MainClass.scala"))
                    .build())
            .addTarget(
                TargetIdeInfo.builder()
                    .setKind("scala_binary")
                    .setLabel("//com/google/binary:OtherName")
                    .setJavaInfo(JavaIdeInfo.builder().setMainClass("com.google.binary.MainClass"))
                    .addSource(sourceRoot("com/google/binary/MainClass.scala"))
                    .build())
            .build());
    registerProjectService(
        BlazeProjectDataManager.class, new MockBlazeProjectDataManager(builder.build()));

    PsiFile scalaFile =
        createAndIndexFile(
            WorkspacePath.createIfValid("com/google/binary/MainClass.scala"),
            "package com.google.binary {",
            "  object MainClass {",
            "    def main(args: Array[String]) {}",
            "  }",
            "}",
            "package scala { final class Array[T] {} }",
            "package java.lang { public final class String {} }");

    RunConfiguration config = createConfigurationFromLocation(scalaFile);

    assertThat(config).isInstanceOf(BlazeRunConfiguration.class);
    BlazeRunConfiguration blazeConfig = (BlazeRunConfiguration) config;
    assertThat(blazeConfig).isNotNull();
    assertThat(blazeConfig.getTarget())
        .isEqualTo(TargetExpression.fromStringSafe("//com/google/binary:OtherName"));
  }
}
