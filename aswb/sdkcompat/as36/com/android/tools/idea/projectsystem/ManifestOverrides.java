/*
 * Copyright 2020 The Bazel Authors. All rights reserved.
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
package com.android.tools.idea.projectsystem;

import com.android.manifmerger.ManifestSystemProperty;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;
import kotlin.text.Regex;

/** Functional copy of ManifestOverrides class from #api4.0 */
public class ManifestOverrides {
  private static Regex PLACEHOLDER_REGEX = new Regex("${([^}]*)}");

  private final Map<ManifestSystemProperty, String> directOverrides;
  private final Map<String, String> placeHolders;

  public Map<ManifestSystemProperty, String> getDirectOverrides() {
    return directOverrides;
  }

  public Map<String, String> getPlaceHolders() {
    return placeHolders;
  }

  public ManifestOverrides(
      @Nullable Map<ManifestSystemProperty, String> directOverrides,
      @Nullable Map<String, String> placeHolders) {
    this.directOverrides = directOverrides == null ? new HashMap<>() : directOverrides;
    this.placeHolders = placeHolders == null ? new HashMap<>() : placeHolders;
  }

  public ManifestOverrides() {
    this(null, null);
  }

  public String resolvePlaceholders(String string) {
    return PLACEHOLDER_REGEX.replace(
        string, matchResult -> placeHolders.getOrDefault(matchResult.getGroupValues().get(1), ""));
  }
}
