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

import com.google.idea.blaze.base.ideinfo.AndroidIdeInfo;
import com.google.idea.blaze.base.ideinfo.LibraryArtifact;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.sync.projectview.WorkspaceLanguageSettings;
import com.google.idea.blaze.java.sync.BlazeJavaSyncAugmenter;
import com.google.idea.blaze.java.sync.model.BlazeJarLibrary;
import java.util.Collection;

/** Augments the java sync process with Android lite support. */
public class BlazeAndroidLiteJavaSyncAugmenter implements BlazeJavaSyncAugmenter {

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

    // Add R.java jars
    LibraryArtifact resourceJar = androidIdeInfo.getResourceJar();
    if (resourceJar != null) {
      jars.add(new BlazeJarLibrary(resourceJar));
    }

    LibraryArtifact idlJar = androidIdeInfo.getIdlJar();
    if (idlJar != null) {
      genJars.add(new BlazeJarLibrary(idlJar));
    }
  }
}
