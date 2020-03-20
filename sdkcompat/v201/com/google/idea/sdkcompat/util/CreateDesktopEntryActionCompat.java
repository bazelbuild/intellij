/*
 * Copyright 2019 The Bazel Authors. All rights reserved.
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
package com.google.idea.sdkcompat.util;

import com.intellij.openapi.diagnostic.Logger;
import java.io.File;
import java.lang.reflect.Method;
import javax.annotation.Nullable;

/**
 * Compat for {@link com.intellij.ide.actions.CreateDesktopEntryAction}. Remove when #api192 is no
 * longer supported.
 */
public class CreateDesktopEntryActionCompat {
  private static final Logger logger = Logger.getInstance(CreateDesktopEntryActionCompat.class);

  private CreateDesktopEntryActionCompat() {}

  @Nullable
  public static String getLauncherScript() {
    // TODO (arakesh): Remove hack once IJ gets updated to use new plugin api.
    // Hack for IJ2019.3 which uses an older api193 which doesn't contain the Restarter class.
    try {
      // Should raise ClassNotFoundException if Class not found.
      Class<?> cls = Class.forName("com.intellij.ide.actions.CreateDesktopEntryAction");
      // Should throw NoSuchMethodException in new PluginSDK.
      Method method = cls.getMethod("getLauncherScript");
      return (String) method.invoke(null);
    } catch (ReflectiveOperationException e) {
      // Ignore exceptions. We default to using the new Class.
      logger.info(
          "Error Calling `CreateDesktopEntryAction.getLauncherScript()`. Using default"
              + " `Restarter.getIdeStarter()`",
          e);
    }

    try {
      Class<?> cls = Class.forName("com.intellij.util.Restarter");
      Method method = cls.getMethod("getIdeStarter");
      File startFile = (File) method.invoke(null);
      if (startFile == null) {
        return null;
      }

      return startFile.getPath();
    } catch (ReflectiveOperationException e) {
      logger.error("Error calling `Restarter.getIdeStarter()`", e);
    }
    return null;
  }
}
