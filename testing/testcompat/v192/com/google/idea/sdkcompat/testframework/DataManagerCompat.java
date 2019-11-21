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
package com.google.idea.sdkcompat.testframework;

import com.intellij.ide.DataManager;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.impl.ComponentManagerImpl;

/**
 * {@link DataManager} is registered as a service in v193, and as a component in prior versions.
 * This class provides utility methods to replace it in tests.
 *
 * <p>#api192
 */
public class DataManagerCompat {
  public static void replaceDataManager(DataManager dataManager) {
    Application application = ApplicationManager.getApplication();
    ((ComponentManagerImpl) application).registerComponentInstance(DataManager.class, dataManager);
  }

  private DataManagerCompat() {}
}
