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
package com.google.idea.sdkcompat.golang;

import com.goide.psi.impl.GoPackage;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;

/** #api182: compat adapter for changes in 2018.3. */
public abstract class GoPackageCompatAdapter extends GoPackage {

  // api182: isTest constructor parameter removed in 2018.3
  protected GoPackageCompatAdapter(
      Project project, String packageName, boolean isTest, VirtualFile... files) {
    super(project, packageName, isTest, files);
  }
}
