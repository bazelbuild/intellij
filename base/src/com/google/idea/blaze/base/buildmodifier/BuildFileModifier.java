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
package com.google.idea.blaze.base.buildmodifier;

import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.model.primitives.Label;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;

/** Abstraction to modify build files, eg. using buildifier */
public interface BuildFileModifier {

  static BuildFileModifier getInstance() {
    return ServiceManager.getService(BuildFileModifier.class);
  }

  /**
   * Add a new rule to a build file. The rule name and rule kind must be validated before this
   * method, but no guarantees are made about actually being able to add this rule to the build
   * file. An example of why it might fail is the build file might already have a rule with the
   * requested name.
   *
   * @param newRule new rule to create
   * @param ruleKind valid kind of rule (android_library, java_library, etc.)
   * @return true if rule is added to file, false otherwise
   */
  boolean addRule(Project project, Label newRule, Kind ruleKind);
}
