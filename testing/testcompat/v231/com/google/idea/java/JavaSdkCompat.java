/*
 * Copyright 2021 The Bazel Authors. All rights reserved.
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
package com.google.idea.java;

import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.MockSdk;
import com.intellij.openapi.projectRoots.impl.UnknownSdkType;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.util.containers.MultiMap;

/**
 * Provides SDK compatibility shims for base plugin API classes, available to all IDEs during
 * test-time.
 */
public final class JavaSdkCompat {
  private JavaSdkCompat() {}
  /** #api233 to inline */
  public static Sdk getUniqueMockJdk(LanguageLevel languageLevel) {
    MockSdk jdk = (MockSdk) IdeaTestUtil.getMockJdk(languageLevel.toJavaVersion());
    jdk.setName(jdk.getName() + "." + jdk.hashCode());
    jdk.setHomePath(jdk.getHomePath() + "." + jdk.hashCode());
    return jdk;
  }

  /** #api233 to remove */
  public static Sdk getNonJavaMockSdk() {
    return new MockSdk(
            /* name= */ "",
            /* homePath= */ "",
            /* versionString= */ "",
            /* roots= */ MultiMap.empty(),
            UnknownSdkType.getInstance(""));
  }
}
