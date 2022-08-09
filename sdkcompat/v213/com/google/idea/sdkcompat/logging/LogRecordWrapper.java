package com.google.idea.sdkcompat.logging;

import java.util.logging.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.spi.LoggingEvent;
import org.jetbrains.annotations.VisibleForTesting;

/**
 * #api213: Use {@link java.util.logging.LogRecord} directly everywhere instead of {@link
 * LogRecordWrapper}
 */
public class LogRecordWrapper {

  private final LoggingEvent loggingEvent;

  LogRecordWrapper(LoggingEvent loggingEvent) {
    this.loggingEvent = loggingEvent;
  }

  @VisibleForTesting
  public LogRecordWrapper(Level level, String message, Throwable throwable) {
    loggingEvent =
        new LoggingEvent(
            "ignored",
            Logger.getLogger("ignored"),
            LoggerWrapper.LEVEL_MAP.get(level),
            message,
            throwable);
  }

  public String getLoggerName() {
    return loggingEvent.getLoggerName();
  }

  public Level getLevel() {
    if (LoggerWrapper.LEVEL_MAP.getKeysByValue(loggingEvent.getLevel()).isEmpty()) {
      return Level.INFO;
    }
    return LoggerWrapper.LEVEL_MAP.getKeysByValue(loggingEvent.getLevel()).get(0);
  }

  public String getMessage() {
    return loggingEvent.getRenderedMessage();
  }

  public Throwable getThrown() {
    if (loggingEvent.getThrowableInformation() == null) {
      return null;
    }
    return loggingEvent.getThrowableInformation().getThrowable();
  }

  public long getMillis() {
    return loggingEvent.getTimeStamp();
  }

  public String getSourceClassName() {
    if (loggingEvent.getLocationInformation() != null) {
      return loggingEvent.getLocationInformation().getClassName();
    }
    return null;
  }

  public String getSourceMethodName() {
    if (loggingEvent.getLocationInformation() != null) {
      return loggingEvent.getLocationInformation().getMethodName();
    }
    return null;
  }
}
