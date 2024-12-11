package com.google.idea.blaze.base.qsync

import com.google.idea.common.util.Transactions
import com.intellij.openapi.application.readAndWriteAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.registry.Registry
import kotlinx.coroutines.runBlocking
import java.util.concurrent.Callable
import java.util.function.Consumer

class ProjectUpdaterThreadingUtils {
    companion object {
        val logger = Logger.getInstance(ProjectUpdaterThreadingUtils::class.java)
        fun <T> readWriteAction(readPart: Callable<T>, commit: Consumer<T>) {
            if (Registry.`is`("bazel.qsync.enable.coroutine.project.updater")) {
                runBlocking {
                    readAndWriteAction {
                        logger.info("Starting read operation")
                        val ret = readPart.call()
                        writeAction {
                            commit.accept(ret)
                        }
                    }
                }
            } else {
                Transactions.submitWriteActionTransactionAndWait {
                    val ret = readPart.call()
                    commit.accept(ret)
                }
            }
        }
    }
}