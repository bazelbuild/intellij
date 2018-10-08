/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.java.sync;

import com.google.common.base.Strings;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.java.projectview.JavaLanguageLevelSection;
import com.google.idea.blaze.java.sync.model.BlazeJavaSyncData;
import com.intellij.pom.java.LanguageLevel;

/** Called by sync plugins to determine the appropriate java language level. */
public class JavaLanguageLevelHelper {

  public static LanguageLevel getJavaLanguageLevel(
      ProjectViewSet projectViewSet,
      BlazeProjectData blazeProjectData,
      LanguageLevel defaultLanguageLevel) {

    defaultLanguageLevel = getLanguageLevelFromToolchain(blazeProjectData, defaultLanguageLevel);
    return JavaLanguageLevelSection.getLanguageLevel(projectViewSet, defaultLanguageLevel);
  }

  private static LanguageLevel getLanguageLevelFromToolchain(
      BlazeProjectData blazeProjectData, LanguageLevel defaultLanguageLevel) {
    BlazeJavaSyncData blazeJavaSyncData =
        blazeProjectData.getSyncState().get(BlazeJavaSyncData.class);
    if (blazeJavaSyncData != null) {
      String sourceVersion = blazeJavaSyncData.getImportResult().sourceVersion;
      if (!Strings.isNullOrEmpty(sourceVersion)) {
        switch (sourceVersion) {
          case "6":
            return LanguageLevel.JDK_1_6;
          case "7":
            return LanguageLevel.JDK_1_7;
          case "8":
            return LanguageLevel.JDK_1_8;
          case "9":
            return LanguageLevel.JDK_1_9;
        }
      }
    }
    return defaultLanguageLevel;
  }
}
