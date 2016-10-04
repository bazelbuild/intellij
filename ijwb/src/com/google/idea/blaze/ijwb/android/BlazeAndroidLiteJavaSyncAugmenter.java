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
package com.google.idea.blaze.ijwb.android;

import com.google.idea.blaze.base.ideinfo.AndroidRuleIdeInfo;
import com.google.idea.blaze.base.ideinfo.LibraryArtifact;
import com.google.idea.blaze.base.ideinfo.RuleIdeInfo;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.sync.projectview.WorkspaceLanguageSettings;
import com.google.idea.blaze.java.sync.BlazeJavaSyncAugmenter;
import com.google.idea.blaze.java.sync.model.BlazeJarLibrary;
import java.util.Collection;

/** Augments the java sync process with Android lite support. */
public class BlazeAndroidLiteJavaSyncAugmenter extends BlazeJavaSyncAugmenter.Adapter {

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

    // Add R.java jars
    LibraryArtifact resourceJar = androidRuleIdeInfo.resourceJar;
    if (resourceJar != null) {
      jars.add(new BlazeJarLibrary(resourceJar, rule.label));
    }

    LibraryArtifact idlJar = androidRuleIdeInfo.idlJar;
    if (idlJar != null) {
      genJars.add(new BlazeJarLibrary(idlJar, rule.label));
    }
  }
}
