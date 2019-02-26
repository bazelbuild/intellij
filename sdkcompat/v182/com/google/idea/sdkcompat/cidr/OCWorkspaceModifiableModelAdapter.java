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

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.collect.ImmutableList;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Trinity;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.NullableFunction;
import com.jetbrains.cidr.lang.OCLanguageKind;
import com.jetbrains.cidr.lang.toolchains.CidrCompilerSwitches;
import com.jetbrains.cidr.lang.toolchains.CidrToolEnvironment;
import com.jetbrains.cidr.lang.workspace.OCWorkspaceImpl;
import com.jetbrains.cidr.lang.workspace.OCWorkspaceImpl.ModifiableModel;
import com.jetbrains.cidr.lang.workspace.OCWorkspaceImpl.ModifiableModel.Message;
import com.jetbrains.cidr.lang.workspace.OCWorkspaceImpl.ModifiableModel.MessageType;
import com.jetbrains.cidr.lang.workspace.compiler.OCCompilerKind;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

/** Adapter to bridge different SDK versions. */
public class OCWorkspaceModifiableModelAdapter {

  /**
   * Commits the modifiable model and returns any error messages encountered setting up the model
   * (e.g., while running a compiler for feature detection).
   *
   * <p>#api182: model API changed in 2018.3
   */
  public static ImmutableList<String> commit(
      ModifiableModel model,
      int serialVersion,
      CidrToolEnvironment toolEnvironment,
      NullableFunction<File, VirtualFile> fileMapper) {
    model.commit(serialVersion);
    return model.getMessages().stream()
        .filter(m -> m.getType().equals(MessageType.ERROR))
        .map(Message::getText)
        .collect(toImmutableList());
  }

  // #api182: In 2018.3, addConfiguration only takes 2 or 4 parameters
  public static void addConfiguration(
      ModifiableModel workspaceModifiable,
      String id,
      String displayName,
      String shortDisplayName,
      File directory,
      Map<OCLanguageKind, PerLanguageCompilerOpts> configLanguages,
      Map<VirtualFile, PerFileCompilerOpts> configSourceFiles,
      CidrToolEnvironment toolEnvironment,
      NullableFunction<File, VirtualFile> fileMapper) {
    Map<OCLanguageKind, Trinity<OCCompilerKind, File, CidrCompilerSwitches>> compatConfigLanguages =
        new HashMap<>();
    configLanguages.forEach(
        (kind, perLangCompilerOpts) -> {
          compatConfigLanguages.put(kind, perLangCompilerOpts.toTrinity());
        });

    Map<VirtualFile, Pair<OCLanguageKind, CidrCompilerSwitches>> compatConfigFiles =
        new HashMap<>();
    configSourceFiles.forEach(
        (vf, perFileCompilerOpts) -> {
          compatConfigFiles.put(vf, perFileCompilerOpts.toPair());
        });

    workspaceModifiable.addConfiguration(
        id,
        displayName,
        shortDisplayName,
        directory,
        compatConfigLanguages,
        compatConfigFiles,
        toolEnvironment,
        fileMapper);
  }

  public static ModifiableModel getClearedModifiableModel(Project project) {
    return OCWorkspaceImpl.getInstanceImpl(project).getModifiableModel();
  }

  /** Group compiler options for a specific file. #api182 */
  public static class PerFileCompilerOpts {
    final OCLanguageKind kind;
    final CidrCompilerSwitches switches;

    public PerFileCompilerOpts(OCLanguageKind kind, CidrCompilerSwitches switches) {
      this.kind = kind;
      this.switches = switches;
    }

    Pair<OCLanguageKind, CidrCompilerSwitches> toPair() {
      return Pair.create(kind, switches);
    }
  }

  /** Group compiler options for a specific language. #api182 */
  public static class PerLanguageCompilerOpts {
    final OCCompilerKind kind;
    final File compiler;
    final CidrCompilerSwitches switches;

    public PerLanguageCompilerOpts(
        OCCompilerKind kind, File compiler, CidrCompilerSwitches switches) {
      this.kind = kind;
      this.compiler = compiler;
      this.switches = switches;
    }

    Trinity<OCCompilerKind, File, CidrCompilerSwitches> toTrinity() {
      return Trinity.create(kind, compiler, switches);
    }
  }
}
