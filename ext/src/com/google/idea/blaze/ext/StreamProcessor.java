/*
 * Copyright 2023 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.ext;

import com.google.common.util.concurrent.Uninterruptibles;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.concurrent.CountDownLatch;
import java.util.logging.Logger;

class StreamProcessor extends Thread {

  private final InputStream stream;
  private final String first;
  private final CountDownLatch latch;

  private volatile boolean foundFirst;

  private static final Logger logger = Logger.getLogger(StreamProcessor.class.getName());

  public StreamProcessor(InputStream stream) {
    this(stream, null);
  }

  public StreamProcessor(InputStream stream, String first) {
    setDaemon(true);
    this.stream = stream;
    this.first = first;
    this.latch = new CountDownLatch(first == null ? 0 : 1);
    this.foundFirst = false;
    this.start();
  }

  @Override
  public void run() {
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream))) {
      String line = reader.readLine();
      while (line != null) {
        logger.info(line);
        if (line.equals(first)) {
          foundFirst = true;
          latch.countDown();
        }
        line = reader.readLine();
      }
    } catch (IOException e) {
      // Ignore, process errors and exceptions are handled elsewhere.
    } finally {
      latch.countDown();
    }
  }

  public boolean waitForFirstLine() {
    Uninterruptibles.awaitUninterruptibly(latch);
    return foundFirst;
  }
}
