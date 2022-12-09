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
package com.google.idea.blaze.base.qsync;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import java.util.Set;

/** Maintains list of all external aar directories */
@Service
public final class AarDependencyRegistry {
  private final Set<String> aarDirs;

  public AarDependencyRegistry(Project project) {
    this.aarDirs = Sets.newHashSet();
  }

  public void registerAar(String aarDir) {
    aarDirs.add(aarDir);
  }

  public ImmutableList<String> getAarDirs() {
    return ImmutableList.copyOf(aarDirs);
  }
}
