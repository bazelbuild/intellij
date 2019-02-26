/*
 * Copyright 2018 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.sdkcompat.vcs;

import com.intellij.dvcs.MultiRootBranches;
import com.intellij.dvcs.branch.DvcsBranchPopup;
import com.intellij.dvcs.branch.DvcsMultiRootBranchConfig;
import com.intellij.dvcs.branch.DvcsSyncSettings;
import com.intellij.dvcs.repo.AbstractRepositoryManager;
import com.intellij.dvcs.repo.Repository;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.util.Condition;
import java.util.function.Function;
import javax.annotation.Nullable;

/** #api182: LightActionGroup added in 182 */
public abstract class DvcsBranchPopupAdapter<RepoT extends Repository>
    extends DvcsBranchPopup<RepoT> {

  protected DvcsBranchPopupAdapter(
      RepoT currentRepository,
      AbstractRepositoryManager<RepoT> repositoryManager,
      DvcsMultiRootBranchConfig<RepoT> multiRootBranchConfig,
      DvcsSyncSettings vcsSettings,
      Condition<AnAction> preselectActionCondition,
      @Nullable String dimensionKey) {
    super(
        currentRepository,
        repositoryManager,
        multiRootBranchConfig,
        vcsSettings,
        preselectActionCondition,
        dimensionKey);
  }

  protected String getCommonName(Function<RepoT, String> nameSupplier) {
    return MultiRootBranches.getCommonName(
        myRepositoryManager.getRepositories(), nameSupplier::apply);
  }

  @Override
  protected final void fillPopupWithCurrentRepositoryActions(
      DefaultActionGroup popupGroup, @Nullable DefaultActionGroup actions) {
    fillPopupWithCurrentRepositoryActions(
        new DvcsPopupActionGroup(popupGroup), new DvcsPopupActionGroup(actions));
  }

  protected abstract void fillPopupWithCurrentRepositoryActions(
      DvcsPopupActionGroup popupGroup, @Nullable DvcsPopupActionGroup actions);

  // #api182: method changed to use LightActionGroup in 2018.3
  @Override
  protected final void fillWithCommonRepositoryActions(
      DefaultActionGroup popupGroup, AbstractRepositoryManager<RepoT> repositoryManager) {
    fillWithCommonRepositoryActions(new DvcsPopupActionGroup(popupGroup), repositoryManager);
  }

  protected abstract void fillWithCommonRepositoryActions(
      DvcsPopupActionGroup popupGroup, AbstractRepositoryManager<RepoT> repositoryManager);

  // #api182: method changed to return LightActionGroup in 2018.3
  @Override
  protected final DefaultActionGroup createRepositoriesActions() {
    // only supporting a single repository
    return new DefaultActionGroup(null, false);
  }
}
