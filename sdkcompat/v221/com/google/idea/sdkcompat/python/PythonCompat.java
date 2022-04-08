package com.google.idea.sdkcompat.python;

import com.intellij.application.Topics;
import com.intellij.ide.ApplicationInitializedListener;
import com.jetbrains.python.run.PyTracebackParser;

/** Provides SDK compatibility shims for Python classes, available to IntelliJ CE & UE. */
public class PythonCompat {
  private PythonCompat() {}

  /**
   * #api213: AppLifecycleListener$appStarting is removed in 2022.1, move to
   * BlazePyTracebackParser.OverrideUpstreamParser and use
   * ApplicationInitializedListener$componentsInitialized instead
   */
  public static void subscribeToAppStart(PyTracebackParser parser) {
    Topics.subscribe(
        ApplicationInitializedListener.TOPIC,
        /* disposable= */ null,
        new ApplicationInitializedListener() {
          @Override
          public void componentsInitialized() {
            PyTracebackParser.PARSERS[1] = parser;
          }
        });
  }
}
