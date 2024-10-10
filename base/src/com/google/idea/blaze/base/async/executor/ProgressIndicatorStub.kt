package com.google.idea.blaze.base.async.executor

import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.TaskInfo
import com.intellij.openapi.wm.ex.ProgressIndicatorEx

object ProgressIndicatorStub : ProgressIndicatorEx {
  override fun start() { }

  override fun stop() { }

  override fun isRunning(): Boolean { return false }

  override fun cancel() { }

  override fun isCanceled(): Boolean { return false }

  override fun setText(p0: String?) { }

  override fun getText(): String { return "" }

  override fun setText2(p0: String?) { }

  override fun getText2(): String { return "" }

  override fun getFraction(): Double { return 0.0 }

  override fun setFraction(p0: Double) { }

  override fun pushState() { }

  override fun popState() { }

  override fun isModal(): Boolean { return false }

  override fun getModalityState(): ModalityState { return ModalityState.NON_MODAL /* required for backwards compatability */ }

  override fun setModalityProgress(p0: ProgressIndicator?) { }

  override fun isIndeterminate(): Boolean { return false; }

  override fun setIndeterminate(p0: Boolean) { }

  override fun checkCanceled() { }

  override fun isPopupWasShown(): Boolean { return false; }

  override fun isShowing(): Boolean { return false; }

  override fun addStateDelegate(p0: ProgressIndicatorEx) { }

  override fun finish(p0: TaskInfo) { }

  override fun isFinished(p0: TaskInfo): Boolean { return false; }

  override fun wasStarted(): Boolean { return false; }

  override fun processFinish() { }

  override fun initStateFrom(p0: ProgressIndicator) { }
}