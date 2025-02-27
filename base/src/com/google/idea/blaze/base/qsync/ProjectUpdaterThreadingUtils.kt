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
package com.google.idea.blaze.base.qsync

import com.intellij.openapi.application.readAndWriteAction
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.diagnostic.Logger
import kotlinx.coroutines.runBlocking
import java.util.concurrent.Callable
import java.util.function.BiConsumer

class ProjectUpdaterThreadingUtils {
  companion object {
    val logger = Logger.getInstance(ProjectUpdaterThreadingUtils::class.java)

    @JvmStatic
    fun <T, U> readWriteAction(readPart: Callable<Pair<T, U>>, commit: BiConsumer<T, U>) {
      runBlocking {
        readAndWriteAction {
          logger.info("Starting read operation")
          val (t, u) = readPart.call();
          writeAction {
            commit.accept(t, u)
          }
        }
      }
    }

    @JvmStatic
    fun performWriteAction(action: Runnable) {
      runBlocking {
        writeAction {
          action.run()
        }
      }
    }
  }
}