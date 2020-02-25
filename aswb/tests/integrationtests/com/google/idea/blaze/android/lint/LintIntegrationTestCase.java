/*
 * Copyright 2020 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.android.lint;

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.BlazeIntegrationTestCase;
import com.intellij.facet.FacetManager;
import com.intellij.facet.ModifiableFacetModel;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleTypeId;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.EdtTestUtil;
import java.util.Arrays;
import org.jetbrains.android.facet.AndroidFacet;
import org.junit.Before;

/** Base Test Case for Lint Integration Tests. */
public abstract class LintIntegrationTestCase extends BlazeIntegrationTestCase {

  @Before
  public void lintIntegrationSetup() {
    runWriteAction(
        () -> {
          // Add modules to the project is required.
          if (!isLightTestCase() && getModuleNames().size() > 0) {
            ModuleManager moduleManager = ModuleManager.getInstance(getProject());
            getModuleNames().forEach(m -> moduleManager.newModule(m, ModuleTypeId.JAVA_MODULE));
          }
        });
  }

  protected final void addContentEntryToModule(Module module, VirtualFile... files) {
    if (files.length == 0) {
      return;
    }
    runWriteAction(
        () -> {
          ModifiableRootModel modifiableModel =
              ModuleRootManager.getInstance(module).getModifiableModel();
          Arrays.asList(files).forEach(modifiableModel::addContentEntry);
          modifiableModel.commit();
        });
  }

  protected final void attachFacetToModule(Module module, AndroidFacet facet) {
    runWriteAction(
        () -> {
          ModifiableFacetModel modifiableFacetModel =
              FacetManager.getInstance(module).createModifiableModel();
          modifiableFacetModel.addFacet(facet);
          modifiableFacetModel.commit();
        });
  }

  /**
   * Override to add modules to the project. {@link BlazeIntegrationTestCase#isLightTestCase()} must
   * return `false` for modules to be added.
   */
  protected ImmutableList<String> getModuleNames() {
    return ImmutableList.of();
  }

  private static void runWriteAction(Runnable writeAction) {
    EdtTestUtil.runInEdtAndWait(
        () -> ApplicationManager.getApplication().runWriteAction(writeAction));
  }
}
