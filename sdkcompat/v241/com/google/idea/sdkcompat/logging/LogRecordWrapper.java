/*
 * Copyright 2024 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.idea.sdkcompat.logging;

import java.util.logging.Level;
import java.util.logging.LogRecord;
import org.jetbrains.annotations.VisibleForTesting;

/** #api213: Use {@link LogRecord} directly everywhere instead of {@link LogRecordWrapper} */
public class LogRecordWrapper {

  private final LogRecord logRecord;

  LogRecordWrapper(LogRecord logRecord) {
    this.logRecord = logRecord;
  }

  @VisibleForTesting
  public LogRecordWrapper(Level level, String message, Throwable throwable) {
    logRecord = new LogRecord(level, message);
    logRecord.setThrown(throwable);
  }

  public String getLoggerName() {
    return logRecord.getLoggerName();
  }

  public Level getLevel() {
    return logRecord.getLevel();
  }

  public String getMessage() {
    return logRecord.getMessage();
  }

  public Throwable getThrown() {
    return logRecord.getThrown();
  }

  public long getMillis() {
    return logRecord.getMillis();
  }

  public String getSourceClassName() {
    return logRecord.getSourceClassName();
  }

  public String getSourceMethodName() {
    return logRecord.getSourceMethodName();
  }
}
