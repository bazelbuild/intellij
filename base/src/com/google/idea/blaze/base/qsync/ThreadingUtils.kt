package com.google.idea.blaze.base.qsync

import com.intellij.openapi.application.readAndWriteAction
import kotlinx.coroutines.runBlocking
import java.util.concurrent.Callable
import java.util.function.Consumer


class ThreadingUtils {
    companion object {
        fun <T> readWriteAction(readPart: Callable<T>, commit: Consumer<T>) {
            runBlocking {
                readAndWriteAction {
                    var ret = readPart.call()
                    writeAction {
                        commit.accept(ret);
                    }
                }
            }
        }
    }
}