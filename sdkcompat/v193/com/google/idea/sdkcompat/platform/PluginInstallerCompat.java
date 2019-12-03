package com.google.idea.sdkcompat.platform;

import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginInstaller;
import java.io.IOException;

/** * Compat for PluginInstaller. Remove when #api192 is no longer supported. */
public class PluginInstallerCompat {

  private PluginInstallerCompat() {}

  public static void prepareToUninstall(IdeaPluginDescriptor pluginDescriptor) throws IOException {
    PluginInstaller.prepareToUninstall(pluginDescriptor);
  }
}
