/*
 * Copyright 2026 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.idea.blaze.base.buildview

import com.google.idea.blaze.base.async.executor.ProgressiveTaskWithProgressIndicator
import com.google.idea.blaze.base.scope.BlazeContext
import com.google.idea.blaze.base.settings.BlazeUserSettings

object BuildViewMigration {
  @JvmStatic
  val enabled
    get(): Boolean = BlazeUserSettings.getInstance().useNewSyncView

  @JvmStatic
  fun present(ctx: BlazeContext): Boolean {
    return ctx.getScope(BuildViewScope::class.java) != null
  }

  @JvmStatic
  fun progressModality(): ProgressiveTaskWithProgressIndicator.Modality {
    return if (enabled) {
      ProgressiveTaskWithProgressIndicator.Modality.BUILD_VIEW
    } else {
      ProgressiveTaskWithProgressIndicator.Modality.ALWAYS_BACKGROUND
    }
  }
}