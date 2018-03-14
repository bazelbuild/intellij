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
package com.google.idea.sdkcompat.plugin;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.ExtensionsArea;
import com.intellij.openapi.extensions.impl.ExtensionPointImpl;
import com.intellij.openapi.extensions.impl.ExtensionsAreaImpl;

/** SDK adapter for {@link ExtensionsArea}, API last modified in 173. */
public class ExtensionsAreaCompatUtils {

  public static <T> ExtensionPointImpl<T> registerExtensionPoint(
      ExtensionsAreaImpl extensionsArea, ExtensionPointName<T> name, Class<T> type) {
    extensionsArea.registerExtensionPoint(name.getName(), type.getName());
    return extensionsArea.getExtensionPoint(name.getName());
  }
}
