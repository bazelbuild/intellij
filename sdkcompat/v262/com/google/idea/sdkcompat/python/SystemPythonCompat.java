/*
 * Copyright 2026 The Bazel Authors. All rights reserved.
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
package com.google.idea.sdkcompat.python;

import com.intellij.openapi.projectRoots.Sdk;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.sdk.PyDetectedSdk;
import com.jetbrains.python.sdk.PySdkExtKt;
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** #api262 */
public final class SystemPythonCompat {
  private SystemPythonCompat() {}

  /** Detects the python interpreters installed on the system, mapped to their language level. */
  public static Map<String, LanguageLevel> detectSystemPythons() {
    Map<String, LanguageLevel> pythons = new LinkedHashMap<>();

    for (PyDetectedSdk sdk :
        PySdkExtKt.detectSystemWideSdks(null, Collections.<Sdk>emptyList())) {
      String homePath = sdk.getHomePath();
      if (homePath == null) {
        continue;
      }

      // PyDetectedSdk does not have a proper version/language level, so go via PythonSdkFlavor
      PythonSdkFlavor<?> flavor = PythonSdkFlavor.getFlavor(homePath);
      pythons.put(
          homePath, flavor == null ? LanguageLevel.getDefault() : flavor.getLanguageLevel(homePath));
    }

    return pythons;
  }
}
