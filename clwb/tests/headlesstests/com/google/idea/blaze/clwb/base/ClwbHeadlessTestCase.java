/*
 * Copyright 2026 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.idea.blaze.clwb.base;

import static com.google.common.truth.Truth.assertThat;
import static com.google.idea.testing.headless.Assertions.abort;

import com.google.idea.testing.headless.HeadlessTestCase;
import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.PathManager;
import com.intellij.testFramework.HeavyPlatformTestCase;
import com.jetbrains.cidr.lang.CLanguageKind;
import com.jetbrains.cidr.lang.OCLanguageKind;
import com.jetbrains.cidr.lang.workspace.OCCompilerSettings;
import com.jetbrains.cidr.lang.workspace.OCResolveConfiguration;
import com.jetbrains.cidr.lang.workspace.OCWorkspace;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

public abstract class ClwbHeadlessTestCase extends HeadlessTestCase {

  @Override
  protected void setUp() throws Exception {
    // Must run before super.setUp(): on 253 rider access <home>/lib during app
    // load (before setUpProject would get a chance to run).
    final var sdkRoot = findSdkRoot();
    linkSandboxDir(sdkRoot, "bin", Path.of(PathManager.getBinPath()));
    linkSandboxDir(sdkRoot, "lib", Path.of(PathManager.getLibPath()));

    super.setUp();
  }

  @Override
  protected void setUpProject() throws Exception {
    super.setUpProject();

    // RadInitialConfigurator (clion-radler) flips AUTO_POPUP_JAVADOC_INFO on
    // the first launch. The tearDown checks compare against default settings.
    setDefaultCodeInsightSettings(CodeInsightSettings.getInstance());
  }

  @Override
  protected void tearDown() throws Exception {
    final var roots = new ArrayList<AllowedVfsRoot>();
    addAllowedVfsRoots(roots);

    Assertions.assertVfsLoads(myBazelInfo.executionRoot(), roots);
    HeavyPlatformTestCase.cleanupApplicationCaches(myProject);

    super.tearDown();
  }

  private static Path findSdkRoot() {
    // app.jar lives at <sdk_root>/lib/app.jar; PathManager.getJarPathForClass
    // works without the application being initialized.
    final var appJar = PathManager.getJarPathForClass(Application.class);
    assertThat(appJar).isNotNull();

    final var libDir = Path.of(appJar).getParent();
    assertThat(libDir).isNotNull();
    assertThat(libDir.getFileName().toString()).isEqualTo("lib");

    return libDir.getParent();
  }

  private static void linkSandboxDir(Path sdkRoot, String dirName, Path link) {
    final var target = sdkRoot.resolve(dirName);
    assertExists(target.toFile());

    try {
      Files.deleteIfExists(link);
      Files.createSymbolicLink(link, target);
    } catch (IOException e) {
      abort("could not create " + dirName + " path symlink", e);
    }
  }

  protected void addAllowedVfsRoots(ArrayList<AllowedVfsRoot> roots) { }

  protected OCWorkspace getWorkspace() {
    return OCWorkspace.getInstance(myProject);
  }

  protected OCResolveConfiguration findFileResolveConfiguration(String relativePath) {
    final var file = findProjectFile(relativePath);

    final var configurations = getWorkspace().getConfigurationsForFile(file);
    assertThat(configurations).hasSize(1);

    return configurations.get(0);
  }

  protected OCCompilerSettings findFileCompilerSettings(String relativePath, OCLanguageKind language) {
    final var file = findProjectFile(relativePath);
    final var resolveConfiguration = findFileResolveConfiguration(relativePath);

    return resolveConfiguration.getCompilerSettings(language, file);
  }

  protected OCCompilerSettings findFileCompilerSettings(String relativePath) {
    return findFileCompilerSettings(relativePath, CLanguageKind.CPP);
  }
}
