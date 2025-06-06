package com.google.idea.blaze.base.buildview

import com.google.idea.blaze.base.scope.BlazeContext
import com.google.idea.blaze.common.PrintOutput
import kotlinx.coroutines.*

fun BlazeContext.println(msg: String) {
  output(PrintOutput(msg))
}

suspend fun <T> BlazeContext.pushJob(
  name: String = "BazelContext",
  block: suspend CoroutineScope.() -> T,
): T {
  return withContext(CoroutineName(name)) {
    addCancellationHandler {
      coroutineContext.job.cancel()
    }

    block()
  }
}