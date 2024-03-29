/*
 * Copyright 2023 The Bazel Authors. All rights reserved.
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
package com.android.tools.idea.rendering;

import com.android.tools.idea.ui.designer.EditorDesignSurface;
import com.android.tools.rendering.RenderResultCompat;
import com.intellij.openapi.actionSystem.DataContext;
import org.jetbrains.annotations.Nullable;

/** Contribute blaze specific render errors. */
public class RenderErrorContributorCompat extends RenderErrorContributor {
  public RenderErrorContributorCompat(
      EditorDesignSurface surface, RenderResultCompat result, @Nullable DataContext dataContext) {
    super(surface, result.get(), dataContext);
  }
}
