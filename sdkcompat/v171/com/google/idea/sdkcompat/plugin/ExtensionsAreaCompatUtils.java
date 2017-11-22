package com.google.idea.sdkcompat.plugin;

import com.intellij.openapi.extensions.DefaultPluginDescriptor;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.ExtensionsArea;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.extensions.impl.ExtensionPointImpl;
import com.intellij.openapi.extensions.impl.ExtensionsAreaImpl;

/** SDK adapter for {@link ExtensionsArea}, added in 173. */
public class ExtensionsAreaCompatUtils {

  public static <T> ExtensionPointImpl<T> registerExtensionPoint(
      ExtensionsAreaImpl extensionsArea, ExtensionPointName<T> name, Class<T> type) {
    PluginDescriptor pluginDescriptor =
        new DefaultPluginDescriptor(PluginId.getId(type.getName()), type.getClassLoader());
    extensionsArea.registerExtensionPoint(name.getName(), type.getName(), pluginDescriptor);
    return extensionsArea.getExtensionPoint(name.getName());
  }
}
