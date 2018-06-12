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
package com.google.idea.blaze.cpp;

import com.google.common.collect.ImmutableMap;
import com.google.idea.blaze.base.logging.EventLoggingService;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.sync.BlazeSyncManager;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationDisplayType;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.roots.impl.storage.ClassPathStorageUtil;
import com.intellij.openapi.roots.impl.storage.ClasspathStorage;
import com.intellij.openapi.roots.libraries.LibraryTable;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import javax.swing.event.HyperlinkEvent;

/**
 * Workaround to undo CMake module modifications, if this is actually a Blaze project.
 *
 * <p>If CMake claims a module before Blaze is able to claim it with {@link
 * com.google.idea.blaze.clwb.BlazeCppModuleType}, then it may make modifications to (1) the
 * "classpath", and (2) content entries + libraries. The libraries may be okay, but the content
 * entries are likely too broad: b/79884939.
 *
 * <p>#api181: After enough modules are migrated to BlazeCppModuleType, perhaps this will become
 * unnecessary. See EventLoggingService data to see how often this comes up.
 */
class CMakeWorkspaceOverride {

  static void undoCMakeModifications(Project project) {
    if (!Blaze.isBlazeProject(project)) {
      return;
    }
    boolean notified = false;
    for (Module module : getCMakeModules(project)) {
      clearClasspath(module);
      clearContentRootsAndLibrariesIfModifiedForCMake(module);
      if (!notified) {
        notified = suggestSyncIfLostContentRootsFromCMake(module);
      }
    }
  }

  /** Get any modules marked with the CMake classpath */
  private static List<Module> getCMakeModules(Project project) {
    // We could check for old CPP_MODULE type, but some modules are already partially migrated to
    // the new BLAZE_CPP_MODULE type. Instead, check for the CMake "classpath".
    return Arrays.stream(ModuleManager.getInstance(project).getModules())
        .filter(module -> "CMake".equals(ClassPathStorageUtil.getStorageType(module)))
        .collect(Collectors.toList());
  }

  private static void clearClasspath(Module module) {
    ClasspathStorage.setStorageType(
        ModuleRootManager.getInstance(module), ClassPathStorageUtil.DEFAULT_STORAGE);
    Logger.getInstance(CMakeWorkspaceOverride.class).warn("Had to clear CMake classpath");
    EventLoggingService.getInstance()
        .ifPresent(
            s ->
                s.logEvent(
                    CMakeWorkspaceOverride.class, "cleared-cmake-classpath", ImmutableMap.of()));
  }

  private static void clearContentRootsAndLibrariesIfModifiedForCMake(Module module) {
    ModuleRootModificationUtil.updateModel(
        module,
        modifiableModel -> {
          if (areLibrariesAndRootsModifiedByCMake(modifiableModel)) {
            // Nuke the roots from orbit.
            modifiableModel.clear();
          }
        });
  }

  private static boolean areLibrariesAndRootsModifiedByCMake(ModifiableRootModel modifiableModel) {
    LibraryTable table = modifiableModel.getModuleLibraryTable();
    return table.getLibraryByName("Header Search Paths") != null;
  }

  private static boolean suggestSyncIfLostContentRootsFromCMake(Module module) {
    // Check if content roots are cleared. Either
    // (1) cleared by clearContentRootsAndLibrariesIfModifiedForCMake, or
    // (2) cleared earlier by CMake before it went and added some roots.
    if (ModuleRootManager.getInstance(module).getContentRoots().length != 0) {
      return false;
    }
    Logger.getInstance(CMakeWorkspaceOverride.class).warn("Found no content roots of CMake module");
    EventLoggingService.getInstance()
        .ifPresent(
            s ->
                s.logEvent(
                    CMakeWorkspaceOverride.class, "cmake-lost-content-entries", ImmutableMap.of()));
    NotificationGroup notificationGroup =
        new NotificationGroup("Undo CMake Module", NotificationDisplayType.STICKY_BALLOON, true);
    NotificationListener notificationListener =
        new NotificationListener.Adapter() {
          @Override
          protected void hyperlinkActivated(Notification notification, HyperlinkEvent event) {
            notification.expire();
            BlazeSyncManager.getInstance(module.getProject()).directoryUpdate(true);
          }
        };
    Notification notification =
        new Notification(
            notificationGroup.getDisplayId(),
            "CMake -> Blaze C++ Module Conversion",
            "Converting C++ modules to Blaze: <a href=\"runSync\">click to fix</a>",
            NotificationType.WARNING,
            notificationListener);
    notification.setImportant(true);
    notification.notify(module.getProject());
    return true;
  }
}
