package com.google.idea.sdkcompat;

import org.picocontainer.ComponentAdapter;

import com.intellij.openapi.components.ComponentManager;
import com.intellij.serviceContainer.ComponentManagerImpl;

// #api212
public class ComponentManagerWrapper {
  private ComponentManager componentManager;

  public ComponentManagerWrapper(ComponentManager componentManager) {
    this.componentManager = componentManager;
  }

  public <T> ComponentAdapter unregisterComponent(String key) {
    return ((ComponentManagerImpl) componentManager.getPicoContainer()).unregisterComponent(key);
  }
}
