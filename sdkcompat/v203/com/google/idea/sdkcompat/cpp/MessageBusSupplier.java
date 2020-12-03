package com.google.idea.sdkcompat.cpp;

import com.intellij.openapi.project.Project;
import com.intellij.util.messages.ListenerDescriptor;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.MessageBusOwner;
import com.intellij.util.messages.impl.MessageBusFactoryImpl;

/** Compat for 2020.1 api changes #api193 */
public class MessageBusSupplier {
  public static MessageBus createMessageBus(Project project) {
    return MessageBusFactoryImpl.createRootBus(new MessageBusOwnerForProject(project));
  }

  private static class MessageBusOwnerForProject implements MessageBusOwner {
    private final Project realOwner;

    MessageBusOwnerForProject(Project project) {
      realOwner = project;
    }

    @Override
    public Object createListener(ListenerDescriptor listenerDescriptor) {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean isDisposed() {
      return realOwner.isDisposed();
    }
  }
}
