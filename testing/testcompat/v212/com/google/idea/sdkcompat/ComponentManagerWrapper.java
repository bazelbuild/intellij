package com.google.idea.sdkcompat;

import org.picocontainer.ComponentAdapter;
import org.picocontainer.MutablePicoContainer;

import com.intellij.openapi.components.ComponentManager;

// #api212
public class ComponentManagerWrapper {
  private ComponentManager componentManager;

  public ComponentManagerWrapper(ComponentManager componentManager) {
    this.componentManager = componentManager;
  }

  public <T> ComponentAdapter unregisterComponent(String key) {
    return ((MutablePicoContainer) componentManager.getPicoContainer()).unregisterComponent(key);
  }
}
