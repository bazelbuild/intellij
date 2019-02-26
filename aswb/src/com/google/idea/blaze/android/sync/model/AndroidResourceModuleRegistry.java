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
import java.util.HashMap;
import java.util.Map;

/** Keeps track of which resource modules correspond to which resource target. */
public class AndroidResourceModuleRegistry {
  private final BiMap<Module, TargetKey> moduleToTarget = HashBiMap.create();
  private final Map<TargetKey, AndroidResourceModule> targetToResourceModule = new HashMap<>();

  public static AndroidResourceModuleRegistry getInstance(Project project) {
    return ServiceManager.getService(project, AndroidResourceModuleRegistry.class);
  }

  public Label getLabel(Module module) {
    TargetKey targetKey = getTargetKey(module);
    return targetKey == null ? null : targetKey.getLabel();
  }

  public TargetKey getTargetKey(Module module) {
    return moduleToTarget.get(module);
  }

  public AndroidResourceModule get(Module module) {
    TargetKey target = moduleToTarget.get(module);
    return target == null ? null : targetToResourceModule.get(target);
  }

  public Module getModule(TargetKey target) {
    return moduleToTarget.inverse().get(target);
  }

  public void put(Module module, AndroidResourceModule resourceModule) {
    moduleToTarget.put(module, resourceModule.targetKey);
    targetToResourceModule.put(resourceModule.targetKey, resourceModule);
  }

  public void clear() {
    moduleToTarget.clear();
    targetToResourceModule.clear();
  }
}
