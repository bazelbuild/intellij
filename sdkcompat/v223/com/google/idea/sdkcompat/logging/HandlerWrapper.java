package com.google.idea.sdkcompat.logging;

import java.util.logging.Handler;
import java.util.logging.LogRecord;

/** #api213: Use {@link Handler} directly instead of {@link HandlerWrapper} everywhere */
public abstract class HandlerWrapper extends Handler {

  @Override
  public void close() {}

  @Override
  public void flush() {}

  @Override
  public void publish(LogRecord logRecord) {
    this.publish(new LogRecordWrapper(logRecord));
  }

  public abstract void publish(LogRecordWrapper logRecord);
}
