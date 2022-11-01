package com.google.idea.sdkcompat.vcs;

import com.intellij.dvcs.branch.DvcsBranchPopup;
import com.intellij.dvcs.branch.DvcsMultiRootBranchConfig;
import com.intellij.dvcs.branch.DvcsSyncSettings;
import com.intellij.dvcs.repo.AbstractRepositoryManager;
import com.intellij.dvcs.repo.Repository;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.util.Condition;

/**
 * Compat for {@link DvcsBranchPopup}. DvcsBranchPopup changed the number of parameters in
 * constructor starting with 2021.2.
 *
 * <p>To cleanup delete the class and use DvcsBranchPopup directly.
 *
 * <p>#api211
 */
public abstract class DvcsBranchPopupCompat<R extends Repository> extends DvcsBranchPopup<R> {

  protected DvcsBranchPopupCompat(
      R currentRepository,
      AbstractRepositoryManager<R> repositoryManager,
      DvcsMultiRootBranchConfig<R> multiRootBranchConfig,
      DvcsSyncSettings vcsSettings,
      Condition<AnAction> preselectActionCondition,
      String dimensionKey,
      DataContext dataContext) {
    super(
        currentRepository,
        repositoryManager,
        multiRootBranchConfig,
        vcsSettings,
        preselectActionCondition,
        dimensionKey,
        dataContext);
  }
}
