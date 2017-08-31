package com.google.idea.sdkcompat.transactions;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;

/** SDK adapter to use transaction guards. */
public class Transactions {
  public static void submitTransactionAndWait(Runnable runnable) {
    ApplicationManager.getApplication().invokeAndWait(runnable, ModalityState.any());
  }

  public static void submitTransaction(Disposable disposable, Runnable runnable) {
    ApplicationManager.getApplication().invokeLater(runnable);
  }

  /** Runs {@link Runnable} as a write action, inside a transaction. */
  public static void submitWriteActionTransactionAndWait(Runnable runnable) {
    submitTransactionAndWait(
        (Runnable) () -> ApplicationManager.getApplication().runWriteAction(runnable));
  }
}
