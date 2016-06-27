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
package com.google.idea.blaze.java.wizard;

import com.google.idea.blaze.base.wizard.ImportSource;
import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.projectImport.ProjectImportProvider;
import org.jetbrains.annotations.Nullable;

/**
 * The import provider for the Blaze plugin.
 */
public class BlazeNewProjectImportProvider extends ProjectImportProvider {

  public BlazeNewProjectImportProvider(BlazeNewJavaProjectImportBuilder builder) {
    super(builder);
  }

  @Override
  protected boolean canImportFromFile(VirtualFile file) {
    return ImportSource.canImport(file);
  }

  @Nullable
  @Override
  public String getFileSample() {
    return "Workspace root, .blazeproject file, or BUILD file";
  }

  @Override
  public String getPathToBeImported(VirtualFile file) {
    return file.getCanonicalPath();
  }

  @Override
  public ModuleWizardStep[] createSteps(WizardContext context) {
    return new ModuleWizardStep[]{new SelectExternalProjectStep(context)};
  }
}
