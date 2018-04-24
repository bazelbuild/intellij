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
package com.google.idea.sdkcompat.clion;

import com.google.common.collect.ImmutableSet;
import com.intellij.openapi.actionSystem.AnAction;
import com.jetbrains.cidr.cpp.cmake.actions.ChangeCMakeProjectContentRootAction;
import com.jetbrains.cidr.cpp.cmake.actions.ClearCMakeCacheAndReloadAction;
import com.jetbrains.cidr.cpp.cmake.actions.ReloadCMakeProjectAction;
import com.jetbrains.cidr.cpp.cmake.actions.ToggleCMakeAutoReloadAction;
import java.util.function.Supplier;

/** Adapter to bridge different SDK versions. */
public class CMakeActionsToManipulate {
  public static final ImmutableSet<String> CMAKE_ACTION_IDS_TO_REMOVE =
      ImmutableSet.of(
          ChangeCMakeProjectContentRootAction.ID,
          ClearCMakeCacheAndReloadAction.ID,
          // 'CMake' -> 'CMake Settings' action: com.cidr.cpp.cmake.actions.OpenCMakeSettingsAction
          "CMake.OpenCMakeSettings",
          ReloadCMakeProjectAction.ID,
          ToggleCMakeAutoReloadAction.ID,
          // 'CMake' > 'Show Generated CMake Files' action:
          //   com.cidr.cpp.cmake.actions.ShowCMakeGeneratedDirAction
          "CMake.ShowCMakeGeneratedDir");

  public static final ImmutableSet<ActionPair> CMAKE_ACTION_IDS_TO_REPLACE =
      ImmutableSet.of(new ActionPair("OpenCPPProject", CMakeOpenProjectActionOverride::new));

  /** Bundle up AnAction ids and AnActions to override the default handler with */
  public static class ActionPair {
    public final String id;
    public final Supplier<AnAction> replacement;

    ActionPair(String id, Supplier<AnAction> replacement) {
      this.id = id;
      this.replacement = replacement;
    }
  }
}
