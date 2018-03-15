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
package com.google.idea.blaze.android.sync.model;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.idea.blaze.base.ideinfo.TargetKey;
import com.google.idea.blaze.base.model.primitives.Label;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;

/** Keeps track of which resource modules correspond to which resource target. */
public class AndroidResourceModuleRegistry {
  private final BiMap<Module, AndroidResourceModule> moduleMap = HashBiMap.create();

  public static AndroidResourceModuleRegistry getInstance(Project project) {
    return ServiceManager.getService(project, AndroidResourceModuleRegistry.class);
  }

  public Label getLabel(Module module) {
    TargetKey targetKey = getTargetKey(module);
    return targetKey == null ? null : targetKey.label;
  }

  public TargetKey getTargetKey(Module module) {
    AndroidResourceModule resourceModule = get(module);
    return resourceModule == null ? null : resourceModule.targetKey;
  }

  public AndroidResourceModule get(Module module) {
    return moduleMap.get(module);
  }

  public void put(Module module, AndroidResourceModule resourceModule) {
    moduleMap.put(module, resourceModule);
  }

  public void clear() {
    moduleMap.clear();
  }
}
