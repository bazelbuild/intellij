package com.google.idea.sdkcompat.logging;

import java.util.logging.Level;
import org.apache.log4j.Appender;
import org.apache.log4j.AppenderSkeleton;
import org.apache.log4j.spi.LoggingEvent;

/**
 * #api213: Use {@link java.util.logging.Handler} directly instead of {@link HandlerWrapper}
 * everywhere
 */
public abstract class HandlerWrapper {

  private final AppenderSkeleton appender;

  public HandlerWrapper() {
    this.appender =
        new AppenderSkeleton() {
          @Override
          protected void append(LoggingEvent loggingEvent) {
            publish(new LogRecordWrapper(loggingEvent));
          }

          @Override
          public void close() {}

          @Override
          public boolean requiresLayout() {
            return false;
          }
        };
  }

  Appender getLogAppender() {
    return appender;
  }

  public abstract void publish(LogRecordWrapper logRecord);

  public void setLevel(Level level) {
    if (!LoggerWrapper.LEVEL_MAP.containsKey(level)) {
      this.appender.setThreshold(org.apache.log4j.Level.INFO);
    } else {
      this.appender.setThreshold(LoggerWrapper.LEVEL_MAP.get(level));
    }
  }
}
