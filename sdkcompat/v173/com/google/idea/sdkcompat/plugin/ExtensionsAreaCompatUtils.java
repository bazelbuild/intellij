package com.google.idea.sdkcompat.plugin;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.ExtensionsArea;
import com.intellij.openapi.extensions.impl.ExtensionPointImpl;
import com.intellij.openapi.extensions.impl.ExtensionsAreaImpl;

/** SDK adapter for {@link ExtensionsArea}, API last modified in 173. */
public class ExtensionsAreaCompatUtils {

  public static <T> ExtensionPointImpl<T> registerExtensionPoint(
      ExtensionsAreaImpl extensionsArea, ExtensionPointName<T> name, Class<T> type) {
    extensionsArea.registerExtensionPoint(name.getName(), type.getName());
    return extensionsArea.getExtensionPoint(name.getName());
  }
}
