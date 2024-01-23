/*
 * Copyright 2022 The Bazel Authors. All rights reserved.
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
package com.google.idea.testing.java;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.intellij.testFramework.fixtures.JavaTestFixtureFactory;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.TestFixtureBuilder;
import com.intellij.util.lang.JavaVersion;

/**
 * Provides SDK compatibility shims for base plugin API classes, available to IDEs working with Java
 * code during test-time.
 */
public class BaseJavaSdkTestCompat {
  private BaseJavaSdkTestCompat() {}

  /** #api213: inline into tests */
  public static TestFixtureBuilder<IdeaProjectTestFixture> createLightFixtureBuilder(
      JavaTestFixtureFactory factory, String projectName) {
    return factory.createLightFixtureBuilder();
  }

  public static TestFixtureBuilder<IdeaProjectTestFixture> createIdeaFixtureBuilder(
      IdeaTestFixtureFactory factory, String name) {
    return factory.createLightFixtureBuilder(LightJavaCodeInsightFixtureTestCase.JAVA_8, name);
  }

  public static void setUpMockJava(JavaVersion version, Disposable disposable) {
    Sdk jdk = IdeaTestUtil.getMockJdk(version);
    EdtTestUtil.runInEdtAndWait(
        () -> WriteAction.run(() -> ProjectJdkTable.getInstance().addJdk(jdk, disposable)));
  }
}
