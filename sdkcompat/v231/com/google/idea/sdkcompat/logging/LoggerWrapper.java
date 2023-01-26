package com.google.idea.sdkcompat.logging;

import java.util.logging.Logger;

/** #api213: Use {@code Logger.getGlobal()} directly into calling classes */
public class LoggerWrapper {

  private LoggerWrapper() {}

  public static void addHandler(HandlerWrapper handler) {
    Logger.getGlobal().addHandler(handler);
  }

  public static void removeHandler(HandlerWrapper handler) {
    Logger.getGlobal().removeHandler(handler);
  }
}
