package com.google.idea.blaze.cpp;

import com.google.idea.blaze.base.scope.BlazeContext;
import com.intellij.openapi.project.Project;
import java.util.Optional;

public class MockXcodeSettingsProvider implements XcodeCompilerSettingsProvider {

  XcodeCompilerSettings settings;

  public void setXcodeSettings(XcodeCompilerSettings settings) {
    this.settings = settings;
  }

  @Override
  public Optional<XcodeCompilerSettings> fromContext(BlazeContext context, Project project)
      throws XcodeCompilerSettingsException {
    if (this.settings != null) {
      return Optional.of(this.settings);
    }
    return Optional.empty();
  }
}
