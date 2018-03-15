/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.android.run.binary;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import java.util.Arrays;
import java.util.List;

/** Provides a list of supported launch methods for android binaries. */
public interface BlazeAndroidBinaryLaunchMethodsProvider {
  ExtensionPointName<BlazeAndroidBinaryLaunchMethodsProvider> EP_NAME =
      ExtensionPointName.create("com.google.idea.blaze.AndroidBinaryLaunchMethodsProvider");

  static AndroidBinaryLaunchMethodComboEntry[] getAllLaunchMethods(Project project) {
    return Arrays.stream(EP_NAME.getExtensions())
        .flatMap(extension -> extension.getLaunchMethods(project).stream())
        .toArray(AndroidBinaryLaunchMethodComboEntry[]::new);
  }

  List<AndroidBinaryLaunchMethodComboEntry> getLaunchMethods(Project project);

  /** All possible binary launch methods. */
  enum AndroidBinaryLaunchMethod {
    NON_BLAZE,
    // Both MOBILE_INSTALL methods have merged.
    // Keep both for backwards compatibility, but in the code both are treated equally.
    // MOBILE_INSTALL is the correct value to use throughout.
    MOBILE_INSTALL,
    MOBILE_INSTALL_V2,
  }

  /** Launch methods wrapped for display in a combo box. */
  class AndroidBinaryLaunchMethodComboEntry {
    final AndroidBinaryLaunchMethod launchMethod;
    private final String description;

    public AndroidBinaryLaunchMethodComboEntry(
        AndroidBinaryLaunchMethod launchMethod, String description) {
      this.launchMethod = launchMethod;
      this.description = description;
    }

    @Override
    public String toString() {
      return description;
    }
  }
}
