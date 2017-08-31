/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.java.sync.projectstructure;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.BlazeIntegrationTestCase;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.IdeaTestUtil;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Integration tests for {@link Jdks}. */
@RunWith(JUnit4.class)
public class JdksTest extends BlazeIntegrationTestCase {

  @Test
  public void testLowerJdkIgnored() {
    setJdks(ImmutableList.of(IdeaTestUtil.getMockJdk14()));
    assertThat(Jdks.findClosestMatch(LanguageLevel.JDK_1_7)).isNull();
  }

  @Test
  public void testEqualJdkChosen() {
    Sdk jdk7 = IdeaTestUtil.getMockJdk17();
    setJdks(ImmutableList.of(jdk7));
    assertThat(Jdks.findClosestMatch(LanguageLevel.JDK_1_7)).isEqualTo(jdk7);
  }

  @Test
  public void testHigherJdkChosen() {
    Sdk jdk8 = IdeaTestUtil.getMockJdk18();
    setJdks(ImmutableList.of(jdk8));
    assertThat(Jdks.findClosestMatch(LanguageLevel.JDK_1_7)).isEqualTo(jdk8);
  }

  @Test
  public void testClosestJdkOfAtLeastSpecifiedLevelChosen() {
    Sdk jdk7 = IdeaTestUtil.getMockJdk17();
    // Ordering retained in final list; add jdk7 last to ensure first Jdk of at least the specified
    // language level isn't automatically chosen.
    setJdks(ImmutableList.of(IdeaTestUtil.getMockJdk18(), IdeaTestUtil.getMockJdk14(), jdk7));
    assertThat(Jdks.findClosestMatch(LanguageLevel.JDK_1_6)).isEqualTo(jdk7);
  }

  private void setJdks(List<Sdk> jdks) {
    List<Sdk> currentJdks =
        ReadAction.compute(
            () -> ProjectJdkTable.getInstance().getSdksOfType(JavaSdk.getInstance()));
    WriteAction.run(
        () -> {
          currentJdks.forEach(jdk -> ProjectJdkTable.getInstance().removeJdk(jdk));
          jdks.forEach(jdk -> ProjectJdkTable.getInstance().addJdk(jdk));
        });
  }
}
