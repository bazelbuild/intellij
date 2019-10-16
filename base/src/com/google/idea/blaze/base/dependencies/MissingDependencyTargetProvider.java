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
package com.google.idea.blaze.base.dependencies;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;

/**
 * Provides the missing Blaze targets that can be added as dependencies to the target rules building
 * the given source file.
 */
public interface MissingDependencyTargetProvider {

  static MissingDependencyTargetProvider getInstance(Project project) {
    return ServiceManager.getService(project, MissingDependencyTargetProvider.class);
  }

  /**
   * Returns the missing dependency targets containing the given referenced elements in their
   * transitive closures that can be added to the targets building the given source file.
   *
   * <p>Each given {@link PsiReference} is a PSI element reference in the specified source file.
   * Specifically, the referenced element is an element in another source file or library. Here are
   * some examples for a Java source file:
   *
   * <ul>
   *   <li>A reference to another class or one of the members of another class from an import
   *       statement.
   *   <li>A reference to another class or one of the members of another class from the code block
   *       itself (such as an inline fully qualified class reference).
   * </ul>
   *
   * <p>If one or more Blaze rules building the given source file are missing the dependency for a
   * {@link PsiReference}, this method returns the list of eligible Blaze target labels that can be
   * added to the affected rule to fix the dependency encapsulated in {@link MissingDependencyData}
   * instance.
   */
  ImmutableList<MissingDependencyData> getMissingDependencyTargets(
      PsiFile sourceFile, ImmutableSet<PsiReference> references);
}
