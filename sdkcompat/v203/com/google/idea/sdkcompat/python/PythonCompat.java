package com.google.idea.sdkcompat.python;

import com.intellij.application.Topics;
import com.intellij.ide.AppLifecycleListener;
import com.intellij.openapi.project.Project;
import com.jetbrains.python.run.PyTracebackParser;
import javax.annotation.Nullable;

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
        AppLifecycleListener.TOPIC,
        /* disposable= */ null,
        new AppLifecycleListener() {
          @Override
          public void appStarting(@Nullable Project projectFromCommandLine) {
            PyTracebackParser.PARSERS[1] = parser;
          }
        });
  }
}
