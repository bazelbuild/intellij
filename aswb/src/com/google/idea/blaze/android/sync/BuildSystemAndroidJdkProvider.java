/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.android.sync;

import com.google.idea.blaze.base.model.BlazeVersionData;
import com.google.idea.blaze.base.settings.Blaze.BuildSystem;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.pom.java.LanguageLevel;
import javax.annotation.Nullable;

/** Provides the highest JDK language level supported by the build system. */
public interface BuildSystemAndroidJdkProvider {

  ExtensionPointName<BuildSystemAndroidJdkProvider> EP_NAME =
      ExtensionPointName.create("com.google.idea.blaze.BuildSystemAndroidJdkProvider");

  LanguageLevel DEFAULT_LANGUAGE_LEVEL = LanguageLevel.JDK_1_7;

  static LanguageLevel languageLevel(BuildSystem buildSystem, BlazeVersionData blazeVersionData) {
    for (BuildSystemAndroidJdkProvider provider : EP_NAME.getExtensions()) {
      LanguageLevel level = provider.getLanguageLevel(buildSystem, blazeVersionData);
      if (level != null) {
        return level;
      }
    }
    return DEFAULT_LANGUAGE_LEVEL;
  }

  /**
   * Returns the highest JDK language level supported for this project, or null if the provider is
   * unable to determine it.
   */
  @Nullable
  LanguageLevel getLanguageLevel(BuildSystem buildSystem, BlazeVersionData blazeVersionData);
}
