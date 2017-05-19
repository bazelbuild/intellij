package com.google.idea.sdkcompat.clion;

import com.google.common.collect.ImmutableSet;
import com.jetbrains.cidr.cpp.cmake.actions.ChangeCMakeProjectContentRootAction;
import com.jetbrains.cidr.cpp.cmake.actions.ClearCMakeCacheAndReloadAction;
import com.jetbrains.cidr.cpp.cmake.actions.OpenCMakeSettingsAction;
import com.jetbrains.cidr.cpp.cmake.actions.ReloadCMakeProjectAction;
import com.jetbrains.cidr.cpp.cmake.actions.ToggleCMakeAutoReloadAction;

/** Handles CMake actions which have changed between our supported versions. */
public class CMakeActionList {

  public static final ImmutableSet<String> CMAKE_ACTION_IDS =
      ImmutableSet.of(
          ChangeCMakeProjectContentRootAction.ID,
          ClearCMakeCacheAndReloadAction.ID,
          OpenCMakeSettingsAction.ID,
          ReloadCMakeProjectAction.ID,
          ToggleCMakeAutoReloadAction.ID,
          // 'CMake' > 'Show Generated CMake Files' action
          "CMake.ShowGeneratedDir");
}
