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

package com.google.idea.blaze.clwb.radler

import com.google.idea.blaze.base.run.smrunner.SmRunnerUtils
import com.intellij.execution.Location
import com.intellij.execution.testframework.sm.runner.SMTestLocator
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope

/**
 * Resolves the generic `blaze:test://<suite>::<test>` location URLs which
 * [com.google.idea.blaze.base.run.smrunner.BlazeTestEventsHandler.testLocationUrl] emits for C/C++ targets, by
 * asking every registered [RadTestFrameworkSupport] in turn.
 */
object RadTestLocator : SMTestLocator, DumbAware {

  override fun getLocation(
    protocol: String,
    path: String,
    project: Project,
    scope: GlobalSearchScope,
  ): List<Location<*>> {
    if (protocol != SmRunnerUtils.GENERIC_TEST_PROTOCOL) {
      return emptyList()
    }

    return RadTestFrameworkSupport.EP_NAME.extensionList
      .firstNotNullOfOrNull { navigable(it.locate(path, project, scope)) }
      ?: emptyList()
  }

  /** Discards results which resolved only to a non-navigable re-run placeholder. */
  private fun navigable(locations: List<Location<*>>): List<Location<*>>? {
    return locations.takeIf { list -> list.any { hasFile(it) } }
  }

  private fun hasFile(location: Location<*>): Boolean {
    // Location.getVirtualFile dereferences getPsiElement without a null check
    return runCatching { location.virtualFile }.getOrNull() != null
  }
}
