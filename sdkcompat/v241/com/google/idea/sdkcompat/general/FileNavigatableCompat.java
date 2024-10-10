package com.google.idea.sdkcompat.general;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.util.Computable;
import com.intellij.platform.backend.navigation.NavigationRequest;
import com.intellij.platform.backend.navigation.impl.RawNavigationRequest;
import com.intellij.pom.Navigatable;
import org.jetbrains.annotations.Nullable;

// #api231 - NavigationRequest api was added in 232
@SuppressWarnings("UnstableApiUsage")
public abstract class FileNavigatableCompat implements Navigatable {

  protected abstract @Nullable OpenFileDescriptor findFileAsync();

  @Override
  public boolean canNavigate() {
    return true;
  }

  @Override
  public boolean canNavigateToSource() {
    return true;
  }

  @Override
  public @Nullable NavigationRequest navigationRequest() {
    if (!canNavigate()) {
      return null;
    }

    final var descriptor = findFileAsync();
    if (descriptor == null || !descriptor.canNavigate()) {
      return null;
    }

    return new RawNavigationRequest(descriptor, descriptor.canNavigateToSource());
  }

  @Override
  public void navigate(boolean requestFocus) {
    final var application = ApplicationManager.getApplication();

    application.executeOnPooledThread(() -> {
      final var descriptor = application.runReadAction((Computable<OpenFileDescriptor>) this::findFileAsync);
      if (descriptor != null && descriptor.canNavigate()) {
        application.invokeLater(() -> descriptor.navigate(requestFocus));
      }
    });
  }
}
