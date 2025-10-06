package com.google.idea.sdkcompat.base;

import com.intellij.openapi.application.ApplicationInfo;

import javax.annotation.Nullable;

public abstract class ApplicationInfoAdapter extends ApplicationInfo {

  @Override
  public @Nullable String getProductUrl() {
    throw new UnsupportedOperationException();
  }

  @Override
  public @Nullable String getJetBrainsTvUrl() {
    throw new UnsupportedOperationException();
  }

}
