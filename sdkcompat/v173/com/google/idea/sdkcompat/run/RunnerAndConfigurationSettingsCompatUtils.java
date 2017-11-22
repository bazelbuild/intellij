package com.google.idea.sdkcompat.run;

import com.intellij.execution.impl.RunnerAndConfigurationSettingsImpl;
import org.jdom.Element;

/** SDK compatibility bridge for {@link RunnerAndConfigurationSettingsImpl}. */
public class RunnerAndConfigurationSettingsCompatUtils {
  public static void readConfiguration(
      RunnerAndConfigurationSettingsImpl settings, Element element) {
    settings.readExternal(element, false);
  }
}
