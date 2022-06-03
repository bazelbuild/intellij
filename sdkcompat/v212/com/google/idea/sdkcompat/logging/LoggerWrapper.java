package com.google.idea.sdkcompat.logging;

import com.intellij.util.containers.BidirectionalMap;
import java.util.logging.Level;
import org.apache.log4j.Logger;

/** #api213: Use {@code java.util.logging.Logger.getGlobal()} directly into calling classes */
public class LoggerWrapper {

  private LoggerWrapper() {}

  static final BidirectionalMap<Level, org.apache.log4j.Level> LEVEL_MAP = new BidirectionalMap<>();

  static {
    LEVEL_MAP.put(Level.INFO, org.apache.log4j.Level.INFO);
    LEVEL_MAP.put(Level.ALL, org.apache.log4j.Level.ALL);
    LEVEL_MAP.put(Level.SEVERE, org.apache.log4j.Level.ERROR);
    LEVEL_MAP.put(Level.WARNING, org.apache.log4j.Level.WARN);
  }

  public static void addHandler(HandlerWrapper handler) {
    Logger.getRootLogger().addAppender(handler.getLogAppender());
  }

  public static void removeHandler(HandlerWrapper handler) {
    Logger.getRootLogger().removeAppender(handler.getLogAppender());
  }
}
