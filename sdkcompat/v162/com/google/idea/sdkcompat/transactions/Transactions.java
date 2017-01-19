package com.google.idea.sdkcompat.transactions;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;

/** Created by tomlu on 12/22/16. */
public class Transactions {
  public static void submitTransactionAndWait(Runnable runnable) {
    ApplicationManager.getApplication().invokeAndWait(runnable, ModalityState.any());
  }

  public static void submitTransaction(Disposable disposable, Runnable runnable) {
    ApplicationManager.getApplication().invokeLater(runnable);
  }
}
