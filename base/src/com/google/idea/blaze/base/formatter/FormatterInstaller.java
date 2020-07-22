/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.formatter;

import com.google.idea.sdkcompat.platform.ServiceHelperCompat;
import com.intellij.openapi.project.Project;
import com.intellij.psi.codeStyle.CodeStyleManager;

/** A utility class to replace the default IntelliJ {@link CodeStyleManager}. */
public final class FormatterInstaller {

  /** A factory for constructing a {@link CodeStyleManager} given the current instance. */
  public interface CodeStyleManagerFactory {
    CodeStyleManager createFormatter(CodeStyleManager delegate);
  }

  /**
   * Replace the existing formatter with one produced from the given {@link CodeStyleManagerFactory}
   */
  public static void replaceFormatter(Project project, CodeStyleManagerFactory newFormatter) {
    CodeStyleManager currentManager = CodeStyleManager.getInstance(project);
    ServiceHelperCompat.registerService(
        project, CodeStyleManager.class, newFormatter.createFormatter(currentManager), project);
  }
}
