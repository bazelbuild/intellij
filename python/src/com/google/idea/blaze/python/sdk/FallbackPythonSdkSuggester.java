/*
 * Copyright 2019 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.python.sdk;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.devtools.intellij.ideinfo.IntellijIdeInfo.PyIdeInfo.PythonVersion;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.python.sync.PySdkSuggester;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.sdk.PyDetectedSdk;
import com.jetbrains.python.sdk.PySdkExtKt;
import com.jetbrains.python.sdk.PythonSdkType;
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor;
import java.io.File;
import java.util.Comparator;
import java.util.List;
import javax.annotation.Nullable;

/** A PySdkSuggester that returns the most recent system interpreter for a given Python version. */
public class FallbackPythonSdkSuggester implements PySdkSuggester {
  private final ImmutableMap<PythonVersion, String> sdks;

  // PyDetectedSdk does not have a proper version/language level, so go via PythonSdkFlavor
  private static LanguageLevel getSdkLanguageLevel(PyDetectedSdk sdk) {
    String sdkHomepath = sdk.getHomePath();
    PythonSdkFlavor flavor = PythonSdkFlavor.getFlavor(sdkHomepath);
    if (flavor == null) {
      return LanguageLevel.getDefault();
    }
    return flavor.getLanguageLevel(sdkHomepath);
  }

  public FallbackPythonSdkSuggester() {
    ImmutableMap.Builder<PythonVersion, String> builder = ImmutableMap.builder();
    List<PyDetectedSdk> detectedSdks = PySdkExtKt.detectSystemWideSdks(null, ImmutableList.of());
    detectedSdks.stream()
        .filter(sdk -> sdk.getHomePath() != null && getSdkLanguageLevel(sdk).isPython2())
        .max(Comparator.comparingInt(sdk -> getSdkLanguageLevel(sdk).getVersion()))
        .ifPresent((sdk) -> builder.put(PythonVersion.PY2, sdk.getHomePath()));
    detectedSdks.stream()
        .filter(sdk -> sdk.getHomePath() != null && getSdkLanguageLevel(sdk).isPy3K())
        .max(Comparator.comparingInt(sdk -> getSdkLanguageLevel(sdk).getVersion()))
        .ifPresent((sdk) -> builder.put(PythonVersion.PY3, sdk.getHomePath()));
    sdks = builder.build();
  }

  @Nullable
  @Override
  public Sdk suggestSdk(Project project, PythonVersion version) {
    if (!Blaze.isBlazeProject(project)) {
      return null;
    }

    if (!sdks.containsKey(version)) {
      return null;
    }

    String homePath = sdks.get(version);
    if (!new File(homePath).exists()) {
      return null;
    }

    Sdk sdk = PySdkSuggester.findPythonSdk(sdks.get(version));
    if (sdk != null) {
      return sdk;
    }

    return SdkConfigurationUtil.createAndAddSDK(homePath, PythonSdkType.getInstance());
  }

  @Override
  public boolean isDeprecatedSdk(Sdk sdk) {
    return false;
  }
}
