package com.google.idea.sdkcompat.cpp;

import com.intellij.openapi.project.Project;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.impl.MessageBusFactoryImpl;

/** Compat for 2020.1 api changes #api193 */
public class MessageBusSupplier {
  public static MessageBus createMessageBus(Project project) {
    return new MessageBusFactoryImpl().createMessageBus(project);
  }
}
