package com.google.idea.blaze.base.sync

import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.runBlockingCancellable
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
        }
        else {
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
