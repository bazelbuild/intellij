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

import com.google.common.base.Objects;
import com.google.idea.blaze.base.lang.buildfile.psi.util.PsiUtils;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.run.producers.BlazeRunConfigurationProducerTestCase;
import com.intellij.execution.lineMarker.RunLineMarkerContributor.Info;
import com.intellij.icons.AllIcons;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Integration tests for {@link BlazeScalaTestRunLineMarkerContributor} */
@RunWith(JUnit4.class)
public class BlazeScalaTestRunLineMarkerContributorTest
    extends BlazeRunConfigurationProducerTestCase {
  private final BlazeScalaTestRunLineMarkerContributor markerContributor =
      new BlazeScalaTestRunLineMarkerContributor();

  @Test
  public void testIgnoreNonTest() throws Throwable {
    PsiFile scalaFile =
        createAndIndexFile(
            WorkspacePath.createIfValid("src/main/scala/com/google/library/Library.scala"),
            "package com.google.library",
            "class Library {",
            "  def method() {}",
            "}");
    List<LeafPsiElement> elements =
        PsiUtils.findAllChildrenOfClassRecursive(scalaFile, LeafPsiElement.class);
    elements.forEach(e -> assertThat(markerContributor.getInfo(e)).isNull());
  }

  @Test
  public void testGetJunitTestInfo() throws Throwable {
    PsiFile junitTestFile =
        createAndIndexFile(
            WorkspacePath.createIfValid("src/test/scala/com/google/test/JunitTest.scala"),
            "package com.google.test {",
            "  class JunitTest {",
            "    @org.junit.Test",
            "    def testMethod() {}",
            "  }",
            "}",
            "package org.junit { trait Test }");
    List<LeafPsiElement> elements =
        PsiUtils.findAllChildrenOfClassRecursive(junitTestFile, LeafPsiElement.class);
    LeafPsiElement classIdentifier =
        elements.stream()
            .filter(e -> Objects.equal(e.getText(), "JunitTest"))
            .findFirst()
            .orElse(null);
    LeafPsiElement methodIdentifier =
        elements.stream()
            .filter(e -> Objects.equal(e.getText(), "testMethod"))
            .findFirst()
            .orElse(null);
    assertThat(classIdentifier).isNotNull();
    assertThat(methodIdentifier).isNotNull();

    Info classInfo = markerContributor.getInfo(classIdentifier);
    assertThat(classInfo).isNotNull();
    assertThat(classInfo.icon).isEqualTo(AllIcons.RunConfigurations.TestState.Run_run);

    Info methodInfo = markerContributor.getInfo(methodIdentifier);
    assertThat(methodInfo).isNotNull();
    assertThat(methodInfo.icon).isEqualTo(AllIcons.RunConfigurations.TestState.Run);

    elements.stream()
        .filter(e -> !Objects.equal(e, classIdentifier) && !Objects.equal(e, methodIdentifier))
        .forEach(e -> assertThat(markerContributor.getInfo(e)).isNull());
  }

  @Test
  public void testGetSpecs2TestInfo() throws Throwable {
    createAndIndexFile(
        WorkspacePath.createIfValid("scala/org/junit/runner/RunWith.scala"),
        "package org.junit.runner",
        "class RunWith");
    createAndIndexFile(
        WorkspacePath.createIfValid("src/test/scala/org/specs2/runner/JUnitRunner.scala"),
        "package org.specs2.runner",
        "class JUnitRunner");
    createAndIndexFile(
        WorkspacePath.createIfValid(
            "src/test/scala/org/specs2/mutable/SpecificationWithJUnit.scala"),
        "package org.specs2.mutable",
        "@org.junit.runner.RunWith(classOf[org.specs2.runner.JUnitRunner])",
        "abstract class SpecificationWithJUnit extends org.specs2.mutable.Specification");
    createAndIndexFile(
        WorkspacePath.createIfValid("src/test/scala/org/specs2/mutable/Specification.scala"),
        "package org.specs2.mutable",
        "abstract class Specification extends org.specs2.mutable.SpecificationLike");
    createAndIndexFile(
        WorkspacePath.createIfValid("src/test/scala/org/specs2/mutable/SpecificationLike.scala"),
        "package org.specs2.mutable",
        "trait SpecificationLike extends",
        "org.specs2.specification.core.mutable.SpecificationStructure");
    createAndIndexFile(
        WorkspacePath.createIfValid(
            "src/test/scala/org/specs2/specification/core/mutable/SpecificationStructure.scala"),
        "package org.specs2.specification.core.mutable",
        "trait SpecificationStructure extends",
        "org.specs2.specification.core.SpecificationStructure");
    createAndIndexFile(
        WorkspacePath.createIfValid(
            "src/test/scala/org/specs2/specification/core/SpecificationStructure.scala"),
        "package org.specs2.specification.core",
        "trait SpecificationStructure");
    PsiFile specs2TestFile =
        createAndIndexFile(
            WorkspacePath.createIfValid("src/test/scala/com/google/test/Specs2Test.scala"),
            "package com.google.test",
            "class Specs2Test extends org.specs2.mutable.SpecificationWithJUnit");
    List<LeafPsiElement> elements =
        PsiUtils.findAllChildrenOfClassRecursive(specs2TestFile, LeafPsiElement.class);
    LeafPsiElement classIdentifier =
        elements.stream()
            .filter(e -> Objects.equal(e.getText(), "Specs2Test"))
            .findFirst()
            .orElse(null);
    assertThat(classIdentifier).isNotNull();

    Info info = markerContributor.getInfo(classIdentifier);
    assertThat(info).isNotNull();
    assertThat(info.icon).isEqualTo(AllIcons.RunConfigurations.TestState.Run_run);

    elements.stream()
        .filter(e -> !Objects.equal(e, classIdentifier))
        .forEach(e -> assertThat(markerContributor.getInfo(e)).isNull());
  }

  @Test
  public void testGetScalaTestInfo() throws Throwable {
    PsiFile scalaTestFile =
        createAndIndexFile(
            WorkspacePath.createIfValid("src/test/scala/com/google/test/ScalaTest.scala"),
            "package com.google.test {",
            "  class ScalaTest extends org.scalatest.FlatSpec {",
            "    \"this test\" should \"pass\" in {}",
            "  }",
            "}",
            "package org.scalatest {",
            "  trait FlatSpec extends Suite",
            "  trait Suite",
            "}");
    List<LeafPsiElement> elements =
        PsiUtils.findAllChildrenOfClassRecursive(scalaTestFile, LeafPsiElement.class);
    LeafPsiElement classIdentifier =
        elements.stream()
            .filter(e -> Objects.equal(e.getText(), "ScalaTest"))
            .findFirst()
            .orElse(null);
    assertThat(classIdentifier).isNotNull();

    Info info = markerContributor.getInfo(classIdentifier);
    assertThat(info).isNotNull();
    assertThat(info.icon).isEqualTo(AllIcons.RunConfigurations.TestState.Run_run);

    elements.stream()
        .filter(e -> !Objects.equal(e, classIdentifier))
        .forEach(e -> assertThat(markerContributor.getInfo(e)).isNull());
  }
}
