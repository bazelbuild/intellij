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
package com.google.idea.blaze.android.sync.importer;

import com.google.idea.blaze.base.ideinfo.AndroidIdeInfo;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import java.util.function.Predicate;
import org.jetbrains.annotations.Nullable;

/** Util class used to verify whether a resource module should be created/ contain sources. */
public final class ModuleTester {
  public static boolean shouldGenerateResources(AndroidIdeInfo androidIdeInfo) {
    // Generate an android resource module if this rule defines resources
    // We don't want to generate one if this depends on a legacy resource rule through :resources
    // In this case, the resource information is redundantly forwarded to this class for
    // backwards compatibility, but the android_resource rule itself is already generating
    // the android resource module
    return androidIdeInfo.generateResourceClass() && androidIdeInfo.getLegacyResources() == null;
  }

  public static boolean shouldGenerateResourceModule(
      AndroidIdeInfo androidIdeInfo, Predicate<ArtifactLocation> whitelistTester) {
    return androidIdeInfo.getResFolders().stream()
        .map(resource -> resource.getRoot())
        .anyMatch(location -> isSourceOrWhitelistedGenPath(location, whitelistTester));
  }

  public static boolean isSourceOrWhitelistedGenPath(
      ArtifactLocation artifactLocation, Predicate<ArtifactLocation> tester) {
    return artifactLocation.isSource() || tester.test(artifactLocation);
  }

  public static boolean shouldCreateModule(
      @Nullable AndroidIdeInfo androidIdeInfo, Predicate<ArtifactLocation> whitelistTester) {
    if (androidIdeInfo == null) {
      return false;
    }
    return shouldGenerateResources(androidIdeInfo)
        && shouldGenerateResourceModule(androidIdeInfo, whitelistTester);
  }
}
