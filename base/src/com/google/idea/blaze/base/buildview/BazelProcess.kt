package com.google.idea.blaze.base.buildview

import com.google.common.util.concurrent.ListenableFuture
import com.google.idea.blaze.exception.BuildException
import com.intellij.execution.process.ProcessHandler
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.guava.asListenableFuture

/**
 * Represents a running bazel process with an eventual result. The actual result
 * depends on the context (i.e. build returns BlazeBuildOutputs and test returns
 * BlazeTestOutputs).
 */
data class BazelProcess<T>(val hdl: ProcessHandler, private val result: Deferred<T>) {

  suspend fun getResult(): T {
    return result.await()
  }

  fun getResultFuture(): ListenableFuture<T> {
    return result.asListenableFuture()
  }

  @Throws(BuildException::class)
  fun getResultBlocking(): T {
    return try {
      getResultFuture().get()
    } catch (e: Exception) {
      throw BuildException("bazel process failed", e)
    }
  }

  fun cancel() {
    if (!hdl.isProcessTerminated) {
      hdl.destroyProcess()
    }

    if (!result.isCancelled) {
      result.cancel()
    }
  }
}
