/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.android.run.test;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import java.util.Arrays;
import java.util.List;

/** Provides a list of supported launch methods for android tests. */
public interface BlazeAndroidTestLaunchMethodsProvider {
  ExtensionPointName<BlazeAndroidTestLaunchMethodsProvider> EP_NAME =
      ExtensionPointName.create("com.google.idea.blaze.AndroidTestLaunchMethodsProvider");

  static AndroidTestLaunchMethodComboEntry[] getAllLaunchMethods(Project project) {
    return Arrays.stream(EP_NAME.getExtensions())
        .flatMap(extension -> extension.getLaunchMethods(project).stream())
        .toArray(AndroidTestLaunchMethodComboEntry[]::new);
  }

  List<AndroidTestLaunchMethodComboEntry> getLaunchMethods(Project project);

  /** All possible test launch methods. */
  enum AndroidTestLaunchMethod {
    NON_BLAZE,
    BLAZE_TEST,
    MOBILE_INSTALL,
  }

  /** Launch methods wrapped for display in a combo box. */
  class AndroidTestLaunchMethodComboEntry {
    final AndroidTestLaunchMethod launchMethod;
    private final String description;

    public AndroidTestLaunchMethodComboEntry(
        AndroidTestLaunchMethod launchMethod, String description) {
      this.launchMethod = launchMethod;
      this.description = description;
    }

    @Override
    public String toString() {
      return description;
    }
  }
}
