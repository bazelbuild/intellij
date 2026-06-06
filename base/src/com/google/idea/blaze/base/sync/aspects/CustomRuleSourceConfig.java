/*
 * Copyright 2026 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.sync.aspects;

import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.intellij.openapi.extensions.ExtensionPointName;
import java.util.Collection;

/**
 * Extension point for configuring custom source attribute collection for Bazel rules.
 *
 * <p>This allows plugins to specify which attributes contain sources for custom rule types,
 * without needing to inject custom Starlark code into the aspect. The aspect will automatically
 * collect files from the specified attributes and treat them as sources for the IDE.
 */
public interface CustomRuleSourceConfig {

  ExtensionPointName<CustomRuleSourceConfig> EP_NAME =
      ExtensionPointName.create("com.google.idea.blaze.CustomRuleSourceConfig");

  /**
   * The Bazel rule kind this configuration applies to.
   *
   * <p>This should match the exact rule name as it appears in BUILD files.
   */
  String getRuleKind();

  /**
   * The language class for this rule (e.g., GO, PYTHON, C, CPP).
   *
   * <p>This is used by the IDE for proper language integration, syntax highlighting,
   * navigation, etc. It should match the primary language of the sources.
   */
  LanguageClass getLanguageClass();

  /**
   * The list of attribute names to collect sources from.
   *
   * <p>The aspect will iterate through these attribute names and collect all files from each
   * attribute. Common attributes include:
   * <ul>
   *   <li>"srcs" - Standard source files attribute</li>
   *   <li>"hdrs" - Header files (for C/C++)</li>
   *   <li>"&lt;rule_kind&gt;_srcs" - Rule-specific sources</li>
   * </ul>
   *
   * <p>Files will be separated into source vs generated files automatically by the aspect.
   *
   * @return A collection of attribute names to collect sources from. Must not be null or empty.
   */
  Collection<String> getSourceAttributes();
}
