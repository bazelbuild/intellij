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
package com.google.idea.sdkcompat.ui;

import com.intellij.openapi.ui.popup.ComponentPopupBuilder;
import java.awt.Color;
import javax.annotation.Nullable;

/** SDK compat utils for {@link ComponentPopupBuilder}; API last modified in 173. */
public final class ComponentPopupBuilderCompatUtils {

  /** Set the color of the popup's border. */
  public static void setBorderColor(ComponentPopupBuilder popupBuilder, @Nullable Color color) {
    // Not supported until 173.
  }
}
