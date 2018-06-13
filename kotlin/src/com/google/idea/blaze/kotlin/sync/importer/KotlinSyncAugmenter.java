/*
 * Copyright 2018 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.kotlin.sync.importer;

import com.google.idea.blaze.base.ideinfo.JavaIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.sync.projectview.WorkspaceLanguageSettings;
import com.google.idea.blaze.java.sync.BlazeJavaSyncAugmenter;
import com.google.idea.blaze.java.sync.model.BlazeJarLibrary;
import java.util.Collection;

/**
 * Temporary workaround for genjars from annotation processors not being exposed in JavaInfo for
 * Kotlin targets.
 */
class KotlinSyncAugmenter implements BlazeJavaSyncAugmenter {

  @Override
  public void addJarsForSourceTarget(
      WorkspaceLanguageSettings workspaceLanguageSettings,
      ProjectViewSet projectViewSet,
      TargetIdeInfo target,
      Collection<BlazeJarLibrary> jars,
      Collection<BlazeJarLibrary> genJars) {
    if (!workspaceLanguageSettings.isLanguageActive(LanguageClass.KOTLIN)
        || target.kind.languageClass != LanguageClass.KOTLIN) {
      return;
    }
    JavaIdeInfo javaInfo = target.javaIdeInfo;
    if (javaInfo == null || javaInfo.filteredGenJar != null || !javaInfo.generatedJars.isEmpty()) {
      return;
    }
    // this is a temporary hack to include annotation processing genjars, by including *all* jars
    // produced by source targets
    // TODO(brendandouglas): remove when kotlin rules expose JavaGenJarsProvider
    javaInfo.jars.forEach(jar -> genJars.add(new BlazeJarLibrary(jar)));
  }
}
