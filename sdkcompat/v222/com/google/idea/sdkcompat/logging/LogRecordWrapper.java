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
