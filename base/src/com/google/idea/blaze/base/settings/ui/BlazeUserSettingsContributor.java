/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.base.settings.ui;

import com.intellij.openapi.extensions.ExtensionPointName;
import javax.swing.JPanel;

/** Allows other modules to contribute user settings. */
public interface BlazeUserSettingsContributor {
  /** Apply UI to settings. */
  void apply();

  /** Reset UI from settings. */
  void reset();

  /** @return Whether any settings in the UI is modified. */
  boolean isModified();

  /** Return the number of components you intend to add. */
  int getRowCount();

  /**
   * Return all components. They will be added to the user settings page.
   *
   * @param panel The panel to add the components to
   * @param rowi The row index to start adding components to.
   * @return The next free row index
   */
  int addComponents(JPanel panel, int rowi);

  /** A provider of user settings. Bind one of these to provide settings. */
  interface Provider {
    ExtensionPointName<Provider> EP_NAME =
        ExtensionPointName.create("com.google.idea.blaze.BlazeUserSettingsContributor");

    BlazeUserSettingsContributor getContributor();
  }
}
