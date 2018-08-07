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

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.ValidationInfo;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** SDK adapter for {@link DialogWrapper#doValidateAll}, added in 172. */
public abstract class DialogWrapperCompatAdapter extends DialogWrapper {

  protected DialogWrapperCompatAdapter(
      @Nullable Project project, boolean canBeParent, @NotNull IdeModalityType ideModalityType) {
    super(project, canBeParent, ideModalityType);
  }

  @Nullable
  @Override
  protected ValidationInfo doValidate() {
    List<ValidationInfo> errors = doValidateAll();
    return errors.isEmpty() ? null : errors.get(0);
  }

  protected abstract List<ValidationInfo> doValidateAll();
}
