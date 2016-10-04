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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.idea.blaze.base.ideinfo.RuleIdeInfo;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.projectview.section.Glob;
import com.google.idea.blaze.base.sync.projectview.WorkspaceLanguageSettings;
import com.google.idea.blaze.java.sync.model.BlazeJarLibrary;
import com.google.idea.blaze.java.sync.model.BlazeLibrary;
import com.intellij.openapi.extensions.ExtensionPointName;
import java.util.Collection;
import java.util.List;

/** Augments the java importer */
public interface BlazeJavaSyncAugmenter {
  ExtensionPointName<BlazeJavaSyncAugmenter> EP_NAME =
      ExtensionPointName.create("com.google.idea.blaze.java.JavaSyncAugmenter");

  static Collection<BlazeJavaSyncAugmenter> getActiveSyncAgumenters(
      WorkspaceLanguageSettings workspaceLanguageSettings) {
    List<BlazeJavaSyncAugmenter> result = Lists.newArrayList();
    for (BlazeJavaSyncAugmenter augmenter : EP_NAME.getExtensions()) {
      if (augmenter.isActive(workspaceLanguageSettings)) {
        result.add(augmenter);
      }
    }
    return result;
  }

  boolean isActive(WorkspaceLanguageSettings workspaceLanguageSettings);

  /**
   * Adds extra libraries for this source rule.
   *
   * @param jars The output jars for the rule. Subject to jdeps optimization.
   * @param genJars Generated jars from this rule. Added unconditionally.
   */
  void addJarsForSourceRule(
      RuleIdeInfo rule, Collection<BlazeJarLibrary> jars, Collection<BlazeJarLibrary> genJars);

  /**
   * Adds any library filters. Useful if some libraries are supplied by this plugin in some other
   * way, eg. via an SDK.
   */
  void addLibraryFilter(Glob.GlobSet excludedLibraries);

  /** Called during the project structure phase to get additional libraries. */
  Collection<BlazeLibrary> getAdditionalLibraries(BlazeProjectData blazeProjectData);

  /**
   * Returns a collection of library names for libraries that are added by some framework and
   * shouldn't be removed during sync. Examples are typescript and dart support.
   */
  Collection<String> getExternallyAddedLibraries(BlazeProjectData blazeProjectData);

  /** Adapter class for the sync augmenter interface */
  abstract class Adapter implements BlazeJavaSyncAugmenter {
    @Override
    public void addJarsForSourceRule(
        RuleIdeInfo rule, Collection<BlazeJarLibrary> jars, Collection<BlazeJarLibrary> genJars) {}

    @Override
    public void addLibraryFilter(Glob.GlobSet excludedLibraries) {}

    @Override
    public Collection<BlazeLibrary> getAdditionalLibraries(BlazeProjectData blazeProjectData) {
      return ImmutableList.of();
    }

    @Override
    public Collection<String> getExternallyAddedLibraries(BlazeProjectData blazeProjectData) {
      return ImmutableList.of();
    }
  }
}
