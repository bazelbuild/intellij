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
import com.google.idea.common.experiments.BoolExperiment;
import com.intellij.pom.java.LanguageLevel;
import javax.annotation.Nullable;

/** Called by sync plugins to determine the appropriate java language level. */
public class JavaLanguageLevelHelper {

  // We need to flexibly switch to Java 11 as default language level at some point in the future.
  // Afterwards, we can remove this flag again.
  private static final BoolExperiment useJava11ForDefaultLanguageLevel =
      new BoolExperiment("java.defaultLanguageLevel.java11.enabled", false);

  public static LanguageLevel getJavaLanguageLevel(
      ProjectViewSet projectViewSet, BlazeProjectData blazeProjectData) {
    LanguageLevel fromToolchain = getLanguageLevelFromToolchain(blazeProjectData);
    return JavaLanguageLevelSection.getLanguageLevel(
        projectViewSet, fromToolchain != null ? fromToolchain : getDefaultLanguageLevel());
  }

  @Nullable
  private static LanguageLevel getLanguageLevelFromToolchain(BlazeProjectData projectData) {
    BlazeJavaSyncData javaSyncData = projectData.getSyncState().get(BlazeJavaSyncData.class);
    if (javaSyncData == null) {
      return null;
    }
    String sourceVersion = javaSyncData.getImportResult().sourceVersion;
    return Strings.isNullOrEmpty(sourceVersion) ? null : LanguageLevel.parse(sourceVersion);
  }

  private static LanguageLevel getDefaultLanguageLevel() {
    return useJava11ForDefaultLanguageLevel.getValue()
        ? LanguageLevel.JDK_11
        : LanguageLevel.JDK_1_8;
  }
}
