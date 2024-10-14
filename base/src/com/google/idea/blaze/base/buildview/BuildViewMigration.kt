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