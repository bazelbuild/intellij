/*
 * Copyright 2022 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.rust;

import com.google.common.collect.ImmutableMap;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.util.PlatformUtils;
import java.util.Map;
import javax.annotation.Nullable;

/** Utilities class related to the IntelliJ Rust plugin. */
public final class RustPluginUtils {

  private RustPluginUtils() {}

  // Order is important, the first matching one will be used.
  private static final ImmutableMap<IDEVersion, String> PRODUCT_TO_PLUGIN_ID =
      ImmutableMap.<IDEVersion, String>builder()
          .put(new IDEVersion(PlatformUtils.CLION_PREFIX), "org.rust.lang")
          .put(new IDEVersion(PlatformUtils.IDEA_PREFIX), "org.rust.lang")
          .put(new IDEVersion(PlatformUtils.IDEA_CE_PREFIX), "org.rust.lang")
          .build();

  /** The Rust plugin ID for this IDE, or null if not available/relevant. */
  @Nullable
  public static String getRustPluginId() {
    for (Map.Entry<IDEVersion, String> rustPluginId : PRODUCT_TO_PLUGIN_ID.entrySet()) {
      if (rustPluginId.getKey().matchesCurrent()) {
        return rustPluginId.getValue();
      }
    }
    return null;
  }

  private static class IDEVersion {
    private final String prefix;

    public IDEVersion(String prefix) {
      this.prefix = prefix;
    }

    public boolean matchesCurrent() {
      ApplicationInfo appInfo = ApplicationInfo.getInstance();
      return PlatformUtils.getPlatformPrefix().equals(prefix);
    }
  }
}
