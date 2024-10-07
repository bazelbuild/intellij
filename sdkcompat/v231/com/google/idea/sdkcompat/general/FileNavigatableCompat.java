package com.google.idea.sdkcompat.general;

import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.pom.Navigatable;
import com.intellij.util.SlowOperations;
import org.jetbrains.annotations.Nullable;

// #api231 - NavigationRequest api was added in 232
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
  public void navigate(boolean requestFocus) {
    final var descriptor = SlowOperations.allowSlowOperations(() -> findFileAsync());
    descriptor.navigate(requestFocus);
  }
}
