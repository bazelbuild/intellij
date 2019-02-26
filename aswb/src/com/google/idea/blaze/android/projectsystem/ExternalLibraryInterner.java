/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.android.projectsystem;

import com.android.projectmodel.ExternalLibrary;
import com.google.common.collect.Interner;
import com.google.common.collect.Interners;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;

/** Project Level service to dedup instances of {@link ExternalLibrary}. */
public class ExternalLibraryInterner {
  private Interner<ExternalLibrary> externalLibraryInterner = Interners.newWeakInterner();

  public static ExternalLibraryInterner getInstance(Project project) {
    return ServiceManager.getService(project, ExternalLibraryInterner.class);
  }

  public ExternalLibrary intern(ExternalLibrary externalLibrary) {
    return externalLibraryInterner.intern(externalLibrary);
  }
}
