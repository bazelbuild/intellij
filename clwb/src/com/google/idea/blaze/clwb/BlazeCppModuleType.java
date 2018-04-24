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
package com.google.idea.blaze.clwb;

import com.intellij.icons.AllIcons;
import com.intellij.ide.util.projectWizard.EmptyModuleBuilder;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.module.ModuleTypeManager;
import javax.swing.Icon;

/** Our own CppModuleType to stop CMake classes trying to attach to projects */
public class BlazeCppModuleType extends ModuleType<EmptyModuleBuilder> {
  private static final String BLAZE_CPP_MODULE = "BLAZE_CPP_MODULE";

  public BlazeCppModuleType() {
    super(BLAZE_CPP_MODULE);
  }

  public static ModuleType<?> getInstance() {
    return ModuleTypeManager.getInstance().findByID(BLAZE_CPP_MODULE);
  }

  @Override
  public EmptyModuleBuilder createModuleBuilder() {
    return new BlazeCppModuleBuilder();
  }

  @Override
  public String getName() {
    return "Blaze C++ Module";
  }

  @Override
  public String getDescription() {
    return getName();
  }

  @Override
  public Icon getNodeIcon(boolean b) {
    return AllIcons.Modules.SourceFolder;
  }

  private static class BlazeCppModuleBuilder extends EmptyModuleBuilder {
    @Override
    public ModuleType<?> getModuleType() {
      return ModuleTypeManager.getInstance().findByID(BLAZE_CPP_MODULE);
    }
  }
}
