package com.google.idea.blaze.base.qsync

import com.intellij.openapi.application.readAndWriteAction
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.diagnostic.Logger
import kotlinx.coroutines.runBlocking
import java.util.concurrent.Callable
import java.util.function.Consumer

class ProjectUpdaterThreadingUtils {
  companion object {
    val logger = Logger.getInstance(ProjectUpdaterThreadingUtils::class.java)
    fun <T> readWriteAction(readPart: Callable<T>, commit: Consumer<T>) {
      runBlocking {
        readAndWriteAction {
          logger.info("Starting read operation")
          val ret = readPart.call();
          writeAction {
            commit.accept(ret)
          }
        }
      }
    }

    fun performWriteAction(action: Runnable) {
      runBlocking {
        writeAction<Unit> {
          action.run()
        }
      }
    }
  }
}