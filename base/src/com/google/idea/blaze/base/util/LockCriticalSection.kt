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

package com.google.idea.blaze.base.util

import com.intellij.openapi.progress.ProgressManager
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Encapsulates the data into [hidden] and allows retriable access to it
 * with cancellation checks
 */
class LockCriticalSection<Hidden : Any>(private val hidden: Hidden) {
  private val lock = Mutex()

  sealed class TryLockResult<out T> {
    data class Acquired<T>(val value: T) : TryLockResult<T>()
    data object NotAcquired : TryLockResult<Nothing>()
  }

  fun <T> tryUse(timeout: Duration? = null, body: Hidden.() -> T): TryLockResult<T> {
    val locked = if (timeout == null) {
      lock.tryLock()
    } else {
      lock.tryLock(timeout)
    }

    if (!locked) {
      return TryLockResult.NotAcquired
    }

    try {
      return TryLockResult.Acquired(hidden.body())
    } finally {
      lock.unlock()
    }
  }

  suspend fun <T> withLock(handler: Hidden.() -> T): T {
    return lock.withLock { handler(hidden) }
  }

  fun <T> withLockInterruptible(handler: Hidden.() -> T): T {
    while (true) {
      ProgressManager.checkCanceled()

      val result = tryUse(100.milliseconds, handler)
      if (result is TryLockResult.Acquired) {
        return result.value
      }
    }
  }
}