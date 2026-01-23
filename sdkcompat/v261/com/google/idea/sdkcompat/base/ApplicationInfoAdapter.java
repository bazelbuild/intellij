package com.google.idea.sdkcompat.base;

import com.intellij.openapi.application.ApplicationInfo;

import javax.annotation.Nullable;

public abstract class ApplicationInfoAdapter extends ApplicationInfo {

  public @Nullable String getProductUrl() {
    throw new UnsupportedOperationException();
  }

  public @Nullable String getJetBrainsTvUrl() {
    throw new UnsupportedOperationException();
  }

}
