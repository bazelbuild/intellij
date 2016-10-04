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

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.android.sync.importer.BlazeAndroidWorkspaceImporter;
import com.google.idea.blaze.android.sync.model.BlazeAndroidSyncData;
import com.google.idea.blaze.base.ideinfo.AndroidRuleIdeInfo;
import com.google.idea.blaze.base.ideinfo.LibraryArtifact;
import com.google.idea.blaze.base.ideinfo.RuleIdeInfo;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.projectview.section.Glob;
import com.google.idea.blaze.base.sync.projectview.WorkspaceLanguageSettings;
import com.google.idea.blaze.java.sync.BlazeJavaSyncAugmenter;
import com.google.idea.blaze.java.sync.model.BlazeJarLibrary;
import com.google.idea.blaze.java.sync.model.BlazeLibrary;
import java.util.Collection;

/** Augments the java sync process with Android support. */
public class BlazeAndroidJavaSyncAugmenter extends BlazeJavaSyncAugmenter.Adapter {

  @Override
  public boolean isActive(WorkspaceLanguageSettings workspaceLanguageSettings) {
    return workspaceLanguageSettings.isLanguageActive(LanguageClass.ANDROID);
  }

  @Override
  public void addJarsForSourceRule(
      RuleIdeInfo rule, Collection<BlazeJarLibrary> jars, Collection<BlazeJarLibrary> genJars) {
    AndroidRuleIdeInfo androidRuleIdeInfo = rule.androidRuleIdeInfo;
    if (androidRuleIdeInfo == null) {
      return;
    }
    LibraryArtifact idlJar = androidRuleIdeInfo.idlJar;
    if (idlJar != null) {
      genJars.add(new BlazeJarLibrary(idlJar, rule.label));
    }

    if (BlazeAndroidWorkspaceImporter.shouldGenerateResources(androidRuleIdeInfo)
        && !BlazeAndroidWorkspaceImporter.shouldGenerateResourceModule(androidRuleIdeInfo)) {
      // Add blaze's output unless it's a top level rule.
      // In these cases the resource jar contains the entire
      // transitive closure of R classes. It's unlikely this is wanted to resolve in the IDE.
      boolean discardResourceJar = rule.kindIsOneOf(Kind.ANDROID_BINARY, Kind.ANDROID_TEST);
      if (!discardResourceJar) {
        LibraryArtifact resourceJar = androidRuleIdeInfo.resourceJar;
        if (resourceJar != null) {
          jars.add(new BlazeJarLibrary(resourceJar, rule.label));
        }
      }
    }
  }

  @Override
  public void addLibraryFilter(Glob.GlobSet excludedLibraries) {
    excludedLibraries.add(new Glob("*/android_blaze.jar")); // This is supplied via the SDK
  }

  @Override
  public Collection<BlazeLibrary> getAdditionalLibraries(BlazeProjectData blazeProjectData) {
    BlazeAndroidSyncData syncData = blazeProjectData.syncState.get(BlazeAndroidSyncData.class);
    if (syncData == null) {
      return ImmutableList.of();
    }
    ImmutableList.Builder<BlazeLibrary> libraries = ImmutableList.builder();
    if (syncData.importResult.resourceLibrary != null) {
      libraries.add(syncData.importResult.resourceLibrary);
    }
    return libraries.build();
  }
}
