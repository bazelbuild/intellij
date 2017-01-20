package com.google.idea.sdkcompat.transactions;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.TransactionGuard;

/** SDK adapter to use transaction guards. */
public class Transactions {
  public static void submitTransactionAndWait(Runnable runnable) {
    TransactionGuard.getInstance().submitTransactionAndWait(runnable);
  }

  public static void submitTransaction(Disposable disposable, Runnable runnable) {
    TransactionGuard.submitTransaction(disposable, runnable);
  }
}
