/*
 * Copyright 2025 The Bazel Authors. All rights reserved.
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

import com.google.idea.blaze.base.model.primitives.Kind
import com.google.idea.blaze.base.model.primitives.LanguageClass
import com.google.idea.blaze.base.model.primitives.RuleType
import com.google.idea.blaze.base.run.smrunner.BlazeTestEventsHandler
import com.intellij.execution.Location
import com.intellij.execution.testframework.sm.runner.SMTestLocator
import com.intellij.openapi.project.Project

class RadTestEventsHandler: BlazeTestEventsHandler {
  override fun handlesKind(kind: Kind?): Boolean {
    return kind != null
        && kind.hasLanguage(LanguageClass.C)
        && kind.getRuleType().equals(RuleType.TEST)
  }

  override fun getTestLocator(): SMTestLocator? {
    // not supported yet
    return null
  }

  override fun getTestFilter(
    project: Project?,
    testLocations: List<Location<*>?>?
  ): String? {
    // not supported yet
    return null
  }
}
