/*
 * Copyright 2018 The Bazel Authors. All rights reserved.
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
package com.google.idea.sdkcompat.cidr;

import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.cidr.lang.OCLanguageKind;
import com.jetbrains.cidr.lang.workspace.OCResolveConfiguration;
import com.jetbrains.cidr.lang.workspace.OCResolveConfigurationImpl;
import com.jetbrains.cidr.lang.workspace.headerRoots.HeadersSearchRoot;
import java.util.List;

/** Compat utilities on {@link OCResolveConfigurationImpl}. */
public class OCResolveConfigurationCompat {
  // #api182: compareConfigurations was moved over to OCResolveConfigurationImpl in v183
  public static int compareConfigurations(OCResolveConfiguration c1, OCResolveConfiguration c2) {
    return OCResolveConfigurationImpl.compareConfigurations(c1, c2);
  }

  // #api183: getHeaderSearchRoots was introduced in v191
  public static List<HeadersSearchRoot> getAllHeaderRoots(
      OCResolveConfiguration configuration, OCLanguageKind kind, VirtualFile rootFile) {
    return configuration.getCompilerSettings(kind, rootFile).getHeadersSearchRoots().getAllRoots();
  }
}
