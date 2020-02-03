/*
 * Copyright 2020 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.javascript;

import com.google.common.collect.ImmutableList;
import com.intellij.lang.javascript.ecmascript6.TypeScriptUtil;
import com.intellij.lang.javascript.library.JSCorePredefinedLibrariesProvider;
import com.intellij.openapi.components.BaseComponent;
import com.intellij.openapi.diagnostic.DefaultLogger;
import com.intellij.openapi.diagnostic.Logger;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * {@link JSCorePredefinedLibrariesProvider} will attempt to find the jsLanguageServicesImpl
 * directory relative to the location of {@link TypeScriptUtil}. It will fail because we did not
 * copy over those files into our plugin API. This test-only application component will suppress the
 * logger errors resulting from this.
 */
class DisableJsLibrariesError implements BaseComponent {
  @Override
  public void initComponent() {
    Logger.setFactory(ErrorIgnoringLogger::new);
  }

  private static class ErrorIgnoringLogger extends DefaultLogger {
    private static final ImmutableList<Class<?>> HANDLED_CLASSES =
        ImmutableList.of(JSCorePredefinedLibrariesProvider.class, TypeScriptUtil.class);
    private static final Pattern IGNORED_ERROR =
        Pattern.compile(
            "Cannot find external directory"
                + "(?: .*/plugins/JavaScriptLanguage/jsLanguageServicesImpl)?"
                + ", the installation is possibly broken.");

    private final boolean shouldIgnoreError;

    ErrorIgnoringLogger(String category) {
      super(category);
      shouldIgnoreError =
          HANDLED_CLASSES.stream()
              .map(Class::getName)
              .map(name -> '#' + name)
              .anyMatch(handled -> Objects.equals(handled, category));
    }

    @Override
    public void error(String message, Throwable t, String... details) {
      if (!shouldIgnoreError || !IGNORED_ERROR.matcher(message).find()) {
        super.error(message, t, details);
      }
    }
  }
}
