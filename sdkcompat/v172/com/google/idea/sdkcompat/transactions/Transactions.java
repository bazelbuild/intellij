package com.google.idea.sdkcompat.transactions;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.TransactionGuard;

/** SDK adapter to use transaction guards. */
public class Transactions {
  public static void submitTransactionAndWait(Runnable runnable) {
    TransactionGuard.getInstance().submitTransactionAndWait(runnable);
  }

  public static void submitTransaction(Disposable disposable, Runnable runnable) {
    TransactionGuard.submitTransaction(disposable, runnable);
  }

  /** Runs {@link Runnable} as a write action, inside a transaction. */
  public static void submitWriteActionTransactionAndWait(Runnable runnable) {
    submitTransactionAndWait(
        (Runnable) () -> ApplicationManager.getApplication().runWriteAction(runnable));
  }
}
