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
package com.google.idea.sdkcompat.cidr;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Trinity;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.NullableFunction;
import com.jetbrains.cidr.lang.OCLanguageKind;
import com.jetbrains.cidr.lang.toolchains.CidrCompilerSwitches;
import com.jetbrains.cidr.lang.toolchains.CidrToolEnvironment;
import com.jetbrains.cidr.lang.workspace.OCWorkspaceImpl;
import com.jetbrains.cidr.lang.workspace.OCWorkspaceImpl.ModifiableModel;
import com.jetbrains.cidr.lang.workspace.compiler.OCCompilerKind;
import java.io.File;
import java.util.Map;

/** Adapter to bridge different SDK versions. */
public class OCWorkspaceModifiableModelAdapter {

  /** This method bridges SDK differences between CLion 2018.1.3 and Android Studio 3.2 #api181 */
  public static void commit(OCWorkspaceImpl.ModifiableModel model, int serialVersion) {
    model.commit(serialVersion);
  }

  // #api182: In 2018.3, addConfiguration only takes 2 or 4 parameters
  public static void addConfiguration(
      ModifiableModel workspaceModifiable,
      String id,
      String displayName,
      String shortDisplayName,
      File directory,
      Map<OCLanguageKind, Trinity<OCCompilerKind, File, CidrCompilerSwitches>> configLanguages,
      Map<VirtualFile, Pair<OCLanguageKind, CidrCompilerSwitches>> configSourceFiles,
      CidrToolEnvironment toolEnvironment,
      NullableFunction<File, VirtualFile> fileMapper) {
    workspaceModifiable.addConfiguration(
        id,
        displayName,
        shortDisplayName,
        directory,
        configLanguages,
        configSourceFiles,
        toolEnvironment,
        fileMapper);
  }
}
