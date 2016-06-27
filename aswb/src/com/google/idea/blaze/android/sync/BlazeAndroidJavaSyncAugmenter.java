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
import com.google.idea.blaze.android.sync.model.BlazeAndroidSyncData;
import com.google.idea.blaze.base.experiments.BoolExperiment;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.projectview.section.Glob;
import com.google.idea.blaze.java.sync.BlazeJavaSyncAugmenter;
import com.google.idea.blaze.java.sync.model.BlazeLibrary;

import java.util.Collection;

/**
 * Augments the java sync process with Android support.
 */
public class BlazeAndroidJavaSyncAugmenter implements BlazeJavaSyncAugmenter {
  private static final BoolExperiment EXCLUDE_ANDROID_BLAZE_JAR = new BoolExperiment("exclude.android.blaze.jar", true);

  @Override
  public void addLibraryFilter(Glob.GlobSet excludedLibraries) {
    if (EXCLUDE_ANDROID_BLAZE_JAR.getValue()) {
      excludedLibraries.add(new Glob("*/android_blaze.jar")); // This is supplied via the SDK
    }
  }

  @Override
  public Collection<BlazeLibrary> getAdditionalLibraries(BlazeProjectData blazeProjectData) {
    BlazeAndroidSyncData syncData = blazeProjectData.syncState.get(BlazeAndroidSyncData.class);
    if (syncData == null) {
      return ImmutableList.of();
    }
    return syncData.importResult.libraries;
  }

  @Override
  public Collection<String> getExternallyAddedLibraries(BlazeProjectData blazeProjectData) {
    return ImmutableList.of();
  }
}
