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
package com.google.idea.blaze.android.sync;

import com.google.idea.blaze.android.sync.importer.BlazeAndroidWorkspaceImporter;
import com.google.idea.blaze.android.sync.importer.BlazeImportUtil;
import com.google.idea.blaze.android.sync.importer.WhitelistFilter;
import com.google.idea.blaze.base.ideinfo.AndroidIdeInfo;
import com.google.idea.blaze.base.ideinfo.LibraryArtifact;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.sync.projectview.WorkspaceLanguageSettings;
import com.google.idea.blaze.java.sync.BlazeJavaSyncAugmenter;
import com.google.idea.blaze.java.sync.model.BlazeJarLibrary;
import java.util.Collection;

/** Augments the java sync process with Android support. */
public class BlazeAndroidJavaSyncAugmenter implements BlazeJavaSyncAugmenter {
  @Override
  public void addJarsForSourceTarget(
      WorkspaceLanguageSettings workspaceLanguageSettings,
      ProjectViewSet projectViewSet,
      TargetIdeInfo target,
      Collection<BlazeJarLibrary> jars,
      Collection<BlazeJarLibrary> genJars) {
    if (!workspaceLanguageSettings.isLanguageActive(LanguageClass.ANDROID)) {
      return;
    }
    AndroidIdeInfo androidIdeInfo = target.getAndroidIdeInfo();
    if (androidIdeInfo == null) {
      return;
    }
    LibraryArtifact idlJar = androidIdeInfo.getIdlJar();
    if (idlJar != null) {
      genJars.add(new BlazeJarLibrary(idlJar));
    }
    if (BlazeAndroidWorkspaceImporter.shouldGenerateResources(androidIdeInfo)
        && !BlazeAndroidWorkspaceImporter.shouldGenerateResourceModule(
            androidIdeInfo,
            new WhitelistFilter(BlazeImportUtil.getWhitelistedGenResourcePaths(projectViewSet)))) {
      // Add blaze's output unless it's a top level rule.
      // In these cases the resource jar contains the entire
      // transitive closure of R classes. It's unlikely this is wanted to resolve in the IDE.
      boolean discardResourceJar = target.kindIsOneOf(Kind.ANDROID_BINARY, Kind.ANDROID_TEST);
      if (!discardResourceJar) {
        LibraryArtifact resourceJar = androidIdeInfo.getResourceJar();
        if (resourceJar != null) {
          jars.add(new BlazeJarLibrary(resourceJar));
        }
      }
    }
  }
}
