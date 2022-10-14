/*
 * Copyright (C) 2018 The Android Open Source Project
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
import static com.google.idea.blaze.android.targetmapbuilder.NbAndroidTarget.android_library;

import com.android.ide.common.repository.GradleCoordinate;
import com.android.tools.idea.projectsystem.GoogleMavenArtifactId;
import com.google.common.collect.ImmutableMap;
import com.google.idea.blaze.android.BlazeAndroidIntegrationTestCase;
import com.google.idea.blaze.android.MockSdkUtil;
import com.google.idea.blaze.android.projectsystem.BlazeModuleSystem;
import com.google.idea.blaze.android.projectsystem.MavenArtifactLocator;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.settings.BuildSystemName;
import com.google.idea.blaze.base.sync.projectstructure.ModuleFinder;
import com.intellij.openapi.module.Module;
import java.nio.file.Path;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Integration test for external dependency management methods in {@link
 * com.google.idea.blaze.android.projectsystem.BlazeModuleSystem}.
 */
@RunWith(JUnit4.class)
public class BlazeModuleSystemExternalDependencyIntegrationTest
    extends BlazeAndroidIntegrationTestCase {
  private static final GradleCoordinate CONSTRAINT_LAYOUT_COORDINATE =
      GoogleMavenArtifactId.CONSTRAINT_LAYOUT.getCoordinate("+");
  private static final String CONSTRAINT_LAYOUT_LABEL =
      "//third_party/java/android/android_sdk_linux/extras/android/compatibility/constraint_layout:constraint_layout";

  @Before
  public void setupSourcesAndProjectView() {
    registerExtension(
        MavenArtifactLocator.EP_NAME,
        new MavenArtifactLocator() {
          private final ImmutableMap<GradleCoordinate, Label> knownArtifacts =
              new ImmutableMap.Builder<GradleCoordinate, Label>()
                  .put(CONSTRAINT_LAYOUT_COORDINATE, Label.create(CONSTRAINT_LAYOUT_LABEL))
                  .build();

          @Override
          public Label labelFor(GradleCoordinate coordinate) {
            return knownArtifacts.get(
                new GradleCoordinate(coordinate.getGroupId(), coordinate.getArtifactId(), "+"));
          }

          @Override
          public BuildSystemName buildSystem() {
            return BuildSystemName.Bazel;
          }
        });

    setProjectView(
        "directories:",
        "  java/com/foo/gallery/activities",
        "targets:",
        "  //java/com/foo/gallery/activities:activities",
        "android_sdk_platform: android-27");
    MockSdkUtil.registerSdk(workspace, "27");

    workspace.createFile(
        new WorkspacePath("java/com/foo/gallery/activities/MainActivity.java"),
        "package com.foo.gallery.activities",
        "import android.app.Activity;",
        "public class MainActivity extends Activity {}");

    workspace.createFile(
        new WorkspacePath("java/com/foo/libs/res/values/styles.xml"),
        "<?xml version=\"1.0\" encoding=\"utf-8\"?>",
        "<resources></resources>");
  }

  @Test
  public void getResolvedDependency_missingDependency() {
    setTargetMap(
        android_library("//java/com/foo/gallery/activities:activities")
            .src("MainActivity.java")
            .dep("//java/com/foo/libs:libs")
            .res("res"),
        android_library("//java/com/foo/libs:libs").res("res"));
    runFullBlazeSyncWithNoIssues();

    Module activityModule =
        ModuleFinder.getInstance(getProject())
            .findModuleByName("java.com.foo.gallery.activities.activities");
    BlazeModuleSystem workspaceModuleSystem = BlazeModuleSystem.getInstance(activityModule);
    assertThat(workspaceModuleSystem.getResolvedDependency(CONSTRAINT_LAYOUT_COORDINATE)).isNull();
  }

  @Test
  public void getResolvedDependency_transitiveDependency() {
    setTargetMap(
        android_library("//java/com/foo/gallery/activities:activities")
            .src("MainActivity.java")
            .dep("//java/com/foo/libs:libs")
            .res("res"),
        android_library("//java/com/foo/libs:libs").res("res").dep(CONSTRAINT_LAYOUT_LABEL),
        android_library(CONSTRAINT_LAYOUT_LABEL));
    runFullBlazeSyncWithNoIssues();

    Module workspaceModule =
        ModuleFinder.getInstance(getProject())
            .findModuleByName("java.com.foo.gallery.activities.activities");
    BlazeModuleSystem workspaceModuleSystem = BlazeModuleSystem.getInstance(workspaceModule);
    assertThat(workspaceModuleSystem.getResolvedDependency(CONSTRAINT_LAYOUT_COORDINATE))
        .isNotNull();
  }

  @Test
  public void getRegisteredDependency_nullForMissingDependency() {
    setTargetMap(
        android_library("//java/com/foo/gallery/activities:activities")
            .src("MainActivity.java")
            .res("res"));
    runFullBlazeSyncWithNoIssues();

    Module workspaceModule =
        ModuleFinder.getInstance(getProject())
            .findModuleByName("java.com.foo.gallery.activities.activities");
    BlazeModuleSystem workspaceModuleSystem = BlazeModuleSystem.getInstance(workspaceModule);
    assertThat(workspaceModuleSystem.getRegisteredDependency(CONSTRAINT_LAYOUT_COORDINATE))
        .isNull();
  }

  @Test
  public void getRegisteredDependency_nullForTransitiveDependency() {
    setTargetMap(
        android_library("//java/com/foo/gallery/activities:activities")
            .src("MainActivity.java")
            .dep("//java/com/foo/libs:libs")
            .res("res"),
        android_library("//java/com/foo/libs:libs").res("res").dep(CONSTRAINT_LAYOUT_LABEL),
        android_library(CONSTRAINT_LAYOUT_LABEL));
    runFullBlazeSyncWithNoIssues();

    Module workspaceModule =
        ModuleFinder.getInstance(getProject())
            .findModuleByName("java.com.foo.gallery.activities.activities");
    BlazeModuleSystem workspaceModuleSystem = BlazeModuleSystem.getInstance(workspaceModule);

    // getRegisteredDependency should return null for a dependency as long as it's not declared by
    // the module itself.
    assertThat(workspaceModuleSystem.getRegisteredDependency(CONSTRAINT_LAYOUT_COORDINATE))
        .isNull();
  }

  @Test
  public void getRegisteredDependency_findsFirstLevelDependency() {
    setTargetMap(
        android_library("//java/com/foo/gallery/activities:activities")
            .src("MainActivity.java")
            .res("res")
            .dep(CONSTRAINT_LAYOUT_LABEL),
        android_library(CONSTRAINT_LAYOUT_LABEL));
    runFullBlazeSyncWithNoIssues();

    Module workspaceModule =
        ModuleFinder.getInstance(getProject())
            .findModuleByName("java.com.foo.gallery.activities.activities");
    BlazeModuleSystem workspaceModuleSystem = BlazeModuleSystem.getInstance(workspaceModule);
    assertThat(workspaceModuleSystem.getRegisteredDependency(CONSTRAINT_LAYOUT_COORDINATE))
        .isNotNull();
  }

  @Test
  public void getDependencyArtifactLocation() {
    setTargetMap(
        android_library("//java/com/foo/gallery/activities:activities")
            .src("MainActivity.java")
            .dep("//java/com/foo/libs:libs")
            .res("res"),
        android_library("//java/com/foo/libs:libs").res("res").dep(CONSTRAINT_LAYOUT_LABEL),
        android_library(CONSTRAINT_LAYOUT_LABEL));
    runFullBlazeSyncWithNoIssues();

    Module workspaceModule =
        ModuleFinder.getInstance(getProject())
            .findModuleByName("java.com.foo.gallery.activities.activities");
    BlazeModuleSystem workspaceModuleSystem = BlazeModuleSystem.getInstance(workspaceModule);
    Path artifactPath = workspaceModuleSystem.getDependencyPath(CONSTRAINT_LAYOUT_COORDINATE);
    assertThat(artifactPath.toString())
        .endsWith(Label.create(CONSTRAINT_LAYOUT_LABEL).blazePackage().relativePath());
  }
}
