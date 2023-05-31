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
package com.android.tools.idea.run;

import com.android.tools.idea.run.ValidationError;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.options.ConfigurationQuickFix;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Compat class for {@link ValidationError} */
public class ValidationErrorCompat {
  public static @NotNull ValidationError fatal(@NotNull String message, @Nullable Runnable quickfix) {
    return ValidationError.fatal(message, dataContext -> quickfix.run());
  }

  private ValidationErrorCompat() {}
}
