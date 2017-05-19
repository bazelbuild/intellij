package com.google.idea.sdkcompat.codestyle;

import com.intellij.psi.codeStyle.CodeStyleManager;

/** Adapter to bridge different SDK versions. */
public abstract class DelegatingCodeStyleManagerSdkCompatAdapter extends CodeStyleManager {

  protected CodeStyleManager delegate;

  protected DelegatingCodeStyleManagerSdkCompatAdapter(CodeStyleManager delegate) {
    this.delegate = delegate;
  }
}
