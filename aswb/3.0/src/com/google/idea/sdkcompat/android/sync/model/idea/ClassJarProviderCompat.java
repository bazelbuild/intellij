/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.sdkcompat.android.sync.model.idea;

import com.android.tools.idea.model.ClassJarProvider;
import com.google.idea.blaze.android.sync.model.idea.BlazeClassJarProvider;
import com.intellij.openapi.module.Module;
import java.io.File;
import java.util.List;

/** Compatibility indirection for {@link BlazeClassJarProvider}. */
public abstract class ClassJarProviderCompat extends ClassJarProvider {
  public abstract List<File> getModuleExternalLibrariesCompat(Module module);

  @Override
  public List<File> getModuleExternalLibraries(Module module) {
    return getModuleExternalLibrariesCompat(module);
  }
}
