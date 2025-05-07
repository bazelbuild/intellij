package com.google.idea.blaze.base.buildview

import com.google.common.util.concurrent.ListenableFuture
import com.google.idea.blaze.exception.BuildException
import com.intellij.execution.process.ProcessHandler
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.guava.asListenableFuture
import kotlinx.coroutines.runBlocking
import java.util.concurrent.CompletableFuture

/**
 * Represents a running bazel process with an eventual result. The actual result
 * depends on the context (i.e. build returns BlazeBuildOutputs and test returns
 * BlazeTestOutputs).
 */
data class BazelProcess<T>(val hdl: ProcessHandler) {

  val processResult = CompletableDeferred<T>()

  @OptIn(ExperimentalCoroutinesApi::class)
  @Throws(BuildException::class)
  fun getResultBlocking(): T {
    return try {
      runBlocking {
        processResult.await()
      }
      processResult.getCompleted()
    } catch (e: Exception) {
      processResult.completeExceptionally(e)
      throw BuildException("bazel process failed", e)
    }
  }

  fun cancel() {
    if (!hdl.isProcessTerminated) {
      hdl.destroyProcess()
    }

    if (!processResult.isCancelled) {
      processResult.cancel()
    }
  }

  fun finishWithResult(result: T) {
    processResult.complete(result)
  }
}
