/*
 * Copyright 2019 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.sync.libraries;

import com.google.common.collect.MapMaker;
import com.google.idea.common.experiments.BoolExperiment;
import com.intellij.ide.FrameStateListener;
import com.intellij.ide.FrameStateManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.TransactionGuard;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.openapi.util.EmptyRunnable;
import java.util.Collections;
import java.util.Set;

class ExternalLibraryUpdater implements ProjectComponent {
  static final BoolExperiment updateExternalSyntheticLibraryOnFrameActivation =
      new BoolExperiment("update.external.synthetic.library.on.frame.activation", true);
  private static final BoolExperiment reindexExternalSyntheticLibraryAfterUpdate =
      new BoolExperiment("reindex.external.synthetic.library.after.update", true);

  private final FrameStateListener listener;
  private final Set<BlazeExternalSyntheticLibrary> externalLibraries;

  static ExternalLibraryUpdater getInstance(Project project) {
    return project.getComponent(ExternalLibraryUpdater.class);
  }

  ExternalLibraryUpdater(Project project) {
    this.listener =
        new FrameStateListener() {
          @Override
          public void onFrameActivated() {
            if (!updateExternalSyntheticLibraryOnFrameActivation.getValue()) {
              return;
            }
            ApplicationManager.getApplication()
                .executeOnPooledThread(
                    () -> {
                      boolean updateHappened = false;
                      for (BlazeExternalSyntheticLibrary library : externalLibraries) {
                        updateHappened |= library.updateValidFiles();
                      }
                      if (updateHappened) {
                        reindexRoots(project);
                      }
                    });
          }
        };
    this.externalLibraries =
        Collections.newSetFromMap(new MapMaker().weakKeys().concurrencyLevel(1).makeMap());
  }

  void reindexRoots(Project project) {
    if (!reindexExternalSyntheticLibraryAfterUpdate.getValue()) {
      return;
    }
    TransactionGuard.submitTransaction(
        project,
        () ->
            WriteAction.run(
                () ->
                    ProjectRootManagerEx.getInstanceEx(project)
                        .makeRootsChange(EmptyRunnable.INSTANCE, false, true)));
  }

  void addExternalLibrary(BlazeExternalSyntheticLibrary library) {
    externalLibraries.remove(library);
    externalLibraries.add(library);
  }

  @Override
  public void projectOpened() {
    FrameStateManager.getInstance().addListener(listener);
  }

  @Override
  public void projectClosed() {
    FrameStateManager.getInstance().removeListener(listener);
  }
}
