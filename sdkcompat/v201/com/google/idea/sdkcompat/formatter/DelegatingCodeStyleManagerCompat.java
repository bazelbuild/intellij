package com.google.idea.sdkcompat.formatter;

import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleManager;

/**
 * Compat for {@code DelegatingCodeStyleManager}. {@link CodeStyleManager} got a new method in
 * 2020.2. #api201
 */
public class DelegatingCodeStyleManagerCompat {

  private DelegatingCodeStyleManagerCompat() {}

  // #api201: Method introduced in 2020.2. Don't do anything for versions <2020.2.
  public static void scheduleReformatWhenSettingsComputed(
      CodeStyleManager delegate, PsiFile file) {}
}
