package com.google.idea.sdkcompat.run;

import com.intellij.execution.impl.RunnerAndConfigurationSettingsImpl;
import com.intellij.openapi.util.InvalidDataException;
import org.jdom.Element;

/** SDK compatibility bridge for {@link RunnerAndConfigurationSettingsImpl}. */
public class RunnerAndConfigurationSettingsCompatUtils {

  public static void readConfiguration(RunnerAndConfigurationSettingsImpl settings, Element element)
      throws InvalidDataException {
    settings.readExternal(element);
  }
}
