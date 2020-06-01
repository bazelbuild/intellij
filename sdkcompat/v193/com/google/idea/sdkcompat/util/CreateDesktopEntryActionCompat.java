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

import com.intellij.util.Restarter;
import java.io.File;
import javax.annotation.Nullable;

/**
 * Compat for {@link com.intellij.ide.actions.CreateDesktopEntryAction}. Remove when #api192 is no
 * longer supported.
 */
public class CreateDesktopEntryActionCompat {
  private CreateDesktopEntryActionCompat() {}

  @Nullable
  public static String getLauncherScript() {
    File startFile = Restarter.getIdeStarter();
    return startFile == null ? null : startFile.getPath();
  }
}
