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
import com.google.idea.blaze.base.run.producer.BlazeRunConfigurationProducerTestCase;
import com.intellij.execution.lineMarker.RunLineMarkerContributor.Info;
import com.intellij.icons.AllIcons;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import java.util.List;
import org.jetbrains.plugins.scala.runner.ScalaRunLineMarkerContributor;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Integration tests for {@link BlazeScalaRunLineMarkerContributor} */
@RunWith(JUnit4.class)
public class BlazeScalaRunLineMarkerContributorTest extends BlazeRunConfigurationProducerTestCase {

  @Test
  public void testGetMainInfo() throws Throwable {
    BlazeScalaRunLineMarkerContributor markerContributor = new BlazeScalaRunLineMarkerContributor();
    ScalaRunLineMarkerContributor replacedContributor = new ScalaRunLineMarkerContributor();

    PsiFile scalaFile =
        createAndIndexFile(
            WorkspacePath.createIfValid("com/google/binary/MainClass.scala"),
            "package com.google.binary {",
            "  object MainClass {",
            "    def main(args: Array[String]) {}",
            "    def foo() {}",
            "  }",
            "}",
            "package scala { final class Array[T] {} }",
            "package java.lang { public final class String {} }");
    List<LeafPsiElement> elements =
        PsiUtils.findAllChildrenOfClassRecursive(scalaFile, LeafPsiElement.class);
    LeafPsiElement objectIdentifier =
        elements.stream()
            .filter(e -> Objects.equal(e.getText(), "MainClass"))
            .findFirst()
            .orElse(null);
    LeafPsiElement methodIdentifier =
        elements.stream().filter(e -> Objects.equal(e.getText(), "main")).findFirst().orElse(null);
    assertThat(objectIdentifier).isNotNull();
    assertThat(methodIdentifier).isNotNull();

    // Have main object info.
    Info objectInfo = markerContributor.getInfo(objectIdentifier);
    assertThat(objectInfo).isNotNull();
    assertThat(objectInfo.icon).isEqualTo(AllIcons.RunConfigurations.TestState.Run);

    // Main object info replaces the one from the scala plugin
    Info replacedObjectInfo = replacedContributor.getInfo(objectIdentifier);
    assertThat(replacedObjectInfo).isNotNull();
    assertThat(objectInfo.shouldReplace(replacedObjectInfo)).isTrue();

    // Hae main method info
    Info methodInfo = markerContributor.getInfo(methodIdentifier);
    assertThat(methodInfo).isNotNull();
    assertThat(methodInfo.icon).isEqualTo(AllIcons.RunConfigurations.TestState.Run);

    // Main method info replaces the one from the scala plugin
    Info replacedMethodInfo = replacedContributor.getInfo(methodIdentifier);
    assertThat(replacedMethodInfo).isNotNull();
    assertThat(methodInfo.shouldReplace(replacedMethodInfo)).isTrue();

    // No other element should get an info
    elements.stream()
        .filter(e -> !Objects.equal(e, objectIdentifier) && !Objects.equal(e, methodIdentifier))
        .forEach(e -> assertThat(markerContributor.getInfo(e)).isNull());
  }
}
