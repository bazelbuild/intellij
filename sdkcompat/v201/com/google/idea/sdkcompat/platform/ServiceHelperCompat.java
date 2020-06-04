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
package com.google.idea.sdkcompat.platform;

import com.google.common.base.Verify;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.ComponentManager;
import com.intellij.serviceContainer.ComponentManagerImpl;
import java.util.List;
import java.util.Optional;

/** #api193: wildcard generics added in 2020.1 */
public class ServiceHelperCompat {
  public static <T> void registerService(
      ComponentManager componentManager,
      Class<T> key,
      T implementation,
      Disposable parentDisposable) {
    @SuppressWarnings({"rawtypes", "unchecked"}) // #api193: wildcard generics added in 2020.1
    List<? extends IdeaPluginDescriptor> loadedPlugins = (List) PluginManager.getLoadedPlugins();
    Optional<? extends IdeaPluginDescriptor> platformPlugin =
        loadedPlugins.stream()
            .filter(descriptor -> descriptor.getName().startsWith("IDEA CORE"))
            .findAny();

    Verify.verify(platformPlugin.isPresent());

    ((ComponentManagerImpl) componentManager)
        .registerServiceInstance(key, implementation, platformPlugin.get());
  }

  private ServiceHelperCompat() {}
}
