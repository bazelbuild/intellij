package com.google.idea.blaze.base.buildview

import com.google.idea.blaze.base.scope.BlazeContext
import com.google.idea.blaze.common.PrintOutput
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async

fun BlazeContext.println(msg: String) {
  output(PrintOutput(msg))
}
