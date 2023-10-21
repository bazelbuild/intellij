package com.google.idea.blaze.cpp;

import com.google.common.collect.ImmutableMap;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.intellij.openapi.project.Project;
import java.util.Optional;

/**
 * Empty implementation of the {@link XcodeCompilerSettingsProvider}, to use in OSes other than
 * macOS.
 * Always returns an empty setting, since Xcode doesn't make sense outside of macOS.
 */
public class XcodeCompilerSettingsProviderNoopImpl implements XcodeCompilerSettingsProvider {

  @Override
  public Optional<XcodeCompilerSettings> fromContext(BlazeContext context, Project project)
      throws XcodeCompilerSettingsException {
    return Optional.empty();
  }
}
