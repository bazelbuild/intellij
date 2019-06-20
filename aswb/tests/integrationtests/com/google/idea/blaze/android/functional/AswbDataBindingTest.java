/*
 * Copyright (C) 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.android.functional;

import static com.google.common.truth.Truth.assertThat;
import static com.google.idea.blaze.android.targetmapbuilder.NbAndroidTarget.android_binary;
import static com.google.idea.blaze.android.targetmapbuilder.NbAndroidTarget.android_library;

import com.android.ide.common.repository.GradleCoordinate;
import com.android.tools.idea.databinding.DataBindingMode;
import com.android.tools.idea.databinding.DataBindingUtil;
import com.android.tools.idea.projectsystem.GoogleMavenArtifactId;
import com.google.idea.blaze.android.BlazeAndroidIntegrationTestCase;
import com.google.idea.blaze.android.projectsystem.MavenArtifactLocator;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.settings.BuildSystem;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.Nullable;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Integration tests for Data Binding support in ASwB. */
@RunWith(JUnit4.class)
public class AswbDataBindingTest extends BlazeAndroidIntegrationTestCase {
  @Before
  public void setup() {
    registerExtension(MavenArtifactLocator.EP_NAME, new DataBindingLocator());
    setProjectView(
        "directories:",
        "  java/com/example",
        "targets:",
        "  //java/com/example/...:all",
        "android_sdk_platform: android-27");
    mockSdk("android-27", "Android 27 SDK");
  }

  @Test
  public void getDataBindingMode() {
    setTargetMap(android_binary("//java/com/example/target:target").res("res"));
    runFullBlazeSync();
    AndroidFacet target = getFacet("java.com.example.target.target");
    AndroidFacet workspace = getWorkspaceFacet();

    assertThat(DataBindingUtil.getDataBindingMode(target)).isEqualTo(DataBindingMode.NONE);
    assertThat(DataBindingUtil.getDataBindingMode(workspace)).isEqualTo(DataBindingMode.NONE);

    setTargetMap(
        android_binary("//java/com/example/target:target").res("res"),
        android_library("//java/com/example/other:other")
            .res("res")
            .dep(DataBindingLocator.DATA_BINDING_LABEL),
        android_library(DataBindingLocator.DATA_BINDING_LABEL));
    runFullBlazeSync();
    AndroidFacet other = getFacet("java.com.example.other.other");

    // The .workspace module should have data binding support if any other module
    // requires it, but a resource module should only have support if it transitively
    // depends on the data binding library.
    assertThat(DataBindingUtil.getDataBindingMode(target)).isEqualTo(DataBindingMode.NONE);
    assertThat(DataBindingUtil.getDataBindingMode(other)).isEqualTo(DataBindingMode.SUPPORT);
    assertThat(DataBindingUtil.getDataBindingMode(workspace)).isEqualTo(DataBindingMode.SUPPORT);

    setTargetMap(
        android_binary("//java/com/example/target:target")
            .res("res")
            .dep("//java/com/example/other:other"),
        android_library("//java/com/example/other:other")
            .res("res")
            .dep(DataBindingLocator.DATA_BINDING_LABEL),
        android_library(DataBindingLocator.DATA_BINDING_LABEL));
    runFullBlazeSync();

    assertThat(DataBindingUtil.getDataBindingMode(target)).isEqualTo(DataBindingMode.SUPPORT);
    assertThat(DataBindingUtil.getDataBindingMode(other)).isEqualTo(DataBindingMode.SUPPORT);
    assertThat(DataBindingUtil.getDataBindingMode(workspace)).isEqualTo(DataBindingMode.SUPPORT);
  }

  private static class DataBindingLocator implements MavenArtifactLocator {
    static final String DATA_BINDING_LABEL = "//java/android/databinding:runtime";

    @Override
    @Nullable
    public Label labelFor(GradleCoordinate coordinate) {
      if (coordinate.getGroupId().equals(GoogleMavenArtifactId.DATA_BINDING_LIB.getMavenGroupId())
          && coordinate
              .getArtifactId()
              .equals(GoogleMavenArtifactId.DATA_BINDING_LIB.getMavenArtifactId())) {
        return Label.create(DATA_BINDING_LABEL);
      }
      return null;
    }

    @Override
    public BuildSystem buildSystem() {
      return BuildSystem.Bazel;
    }
  }
}
