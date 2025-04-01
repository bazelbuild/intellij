package com.google.idea.blaze.base.buildview

import com.google.idea.blaze.base.async.executor.ProgressiveTaskWithProgressIndicator
import com.google.idea.blaze.base.scope.BlazeContext
import com.google.idea.blaze.base.settings.BlazeUserSettings
import com.google.idea.blaze.base.util.pluginProjectScope
import com.intellij.build.BuildContentManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.wm.impl.content.ContentLabel
import com.intellij.ui.ComponentUtil
import com.intellij.ui.GotItTooltip
import com.intellij.ui.content.Content
import com.intellij.util.asSafely
import com.intellij.util.concurrency.ThreadingAssertions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.swing.JComponent

private val PROMO_TOOLTIP_ID = "com.google.idea.blaze.base.buildview.promo"
private val PROMO_TOOLTIP_TEXT = "Enable the new integrated sync view for better error reporting and more information during sync."
private val PROMO_TOOLTIP_HEADR = "Try New Sync-View"
private val PROMO_TOOLTIP_BUTTON = "Enable Now"


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

  private fun findContentLabel(content: Content): JComponent? {
    val contentComponent = content.manager?.component ?: return null

    // the label is only reachable from the window's component
    val windowComponent = contentComponent.parent?.asSafely<JComponent>() ?: return null

    @Suppress("UnstableApiUsage")
    return ComponentUtil.findComponentsOfType(windowComponent, ContentLabel::class.java).firstOrNull()
  }

  private fun enableBuildView(project: Project) {
    pluginProjectScope(project).launch(Dispatchers.EDT) {
      // enable the build view in the settings
      BlazeUserSettings.getInstance().useNewSyncView = true

      // show the user the build tool window
      BuildContentManager.getInstance(project).getOrCreateToolWindow().activate(null)
    }
  }

  @JvmStatic
  fun addPromotionTooltip(project: Project, content: Content) {
    ThreadingAssertions.softAssertEventDispatchThread()

    val label = findContentLabel(content) ?: return

    GotItTooltip(PROMO_TOOLTIP_ID, PROMO_TOOLTIP_TEXT, content)
      .withShowCount(1)
      .withHeader(PROMO_TOOLTIP_HEADR)
      .withPosition(Balloon.Position.above)
      .withSecondaryButton(PROMO_TOOLTIP_BUTTON) { enableBuildView(project) }
      .show(label, GotItTooltip.TOP_MIDDLE)
  }
}