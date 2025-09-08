/*
 * Copyright 2025 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.cpp.sync.aspect

import com.google.common.collect.ImmutableList
import com.google.idea.blaze.base.sync.aspects.storage.AspectModuleWriter

class TransitiveCcDependenciesModule : AspectModuleWriter() {
  
  override fun name() = "transitive_cc_dependencies"

  override fun dependencies(): ImmutableList<Dependency> = ImmutableList.of(
    ruleSetDependency("rules_cc"),
    registryKeyDependency("bazel.cc.includes.cache.enabled"),
  )

  override fun functions(): ImmutableList<Function> = ImmutableList.of(
    Function("module_transitive_cc_dependencies_collect", "struct(providers = [], targets = [])"),
  ) 
}
