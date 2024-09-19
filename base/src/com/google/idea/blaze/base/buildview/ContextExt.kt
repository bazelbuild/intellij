package com.google.idea.blaze.base.buildview

import com.google.idea.blaze.base.scope.BlazeContext
import com.google.idea.blaze.base.scope.BlazeScope
import com.google.idea.blaze.common.PrintOutput
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async

fun BlazeContext.println(msg: String) {
  output(PrintOutput(msg))
}

fun <T> BlazeContext.pushJob(scope: CoroutineScope, block: suspend CoroutineScope.() -> T): T {
  val deferred = scope.async(Dispatchers.IO) { block() }

  push(object : BlazeScope {
    override fun onScopeEnd(context: BlazeContext?) {
      deferred.cancel()
    }
  })

  return runBlockingMaybeCancellable {
    deferred.await()
  }
}