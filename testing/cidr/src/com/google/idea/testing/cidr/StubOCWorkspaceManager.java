/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
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
package com.google.idea.testing.cidr;

import com.google.idea.sdkcompat.cidr.OCWorkspaceModificationTrackersCompatUtils;
import com.intellij.lang.Language;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.TransactionGuard;
import com.intellij.openapi.fileTypes.PlainTextLanguage;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.psi.impl.file.impl.FileManager;
import com.intellij.util.ui.UIUtil;
import com.jetbrains.cidr.lang.OCLanguage;
import com.jetbrains.cidr.lang.preprocessor.OCInclusionContextUtil;
import com.jetbrains.cidr.lang.workspace.OCWorkspace;
import com.jetbrains.cidr.lang.workspace.OCWorkspaceManager;

/**
 * A stub {@link OCWorkspaceManager} to use for testing. Also allows toggling on C++ support (which
 * may have been disabled by other OCWorkspaceManagers.
 *
 * <p>Once the plugin API ships with a more official OCWorkspaceManager-for-testing, we may be able
 * to switch over to those classes. See: b/32420569
 */
public class StubOCWorkspaceManager extends OCWorkspaceManager {

  private final Project project;
  private final OCWorkspace workspace;

  public StubOCWorkspaceManager(Project project) {
    this.project = project;
    this.workspace = new StubOCWorkspace(project);
  }

  @Override
  public OCWorkspace getWorkspace() {
    return workspace;
  }

  /**
   * Enable C++ language support for testing (a previously registered OCWorkspace which may have
   * disabled language support).
   *
   * <p>This function does not work since sdkcompat v173, OCLanguage.LANGUAGE_SUPPORT_DISABLED will
   * still return true after calling enableCSupportForTesting(). RebuildSymbols may have some
   * issues, please read BlazeNdkSupportEnabler.doRebuildSymbols for more details.
   */
  public void enableCSupportForTesting() throws Exception {
    OCWorkspace workspace = OCWorkspaceManager.getWorkspace(project);
    Boolean isCurrentlyEnabled = !OCLanguage.LANGUAGE_SUPPORT_DISABLED.get(project, false);
    if (!isCurrentlyEnabled) {
      enableLanguageSupport(project);
      rebuildSymbols(project, workspace);
    }
  }

  private static void enableLanguageSupport(Project project) {
    OCLanguage.LANGUAGE_SUPPORT_DISABLED.set(project, false);
    UIUtil.invokeLaterIfNeeded(
        () ->
            ApplicationManager.getApplication()
                .runWriteAction(
                    () -> {
                      if (project.isDisposed()) {
                        return;
                      }
                      Language langToReset = PlainTextLanguage.INSTANCE;
                      FileManager fileManager =
                          ((PsiManagerEx) PsiManager.getInstance(project)).getFileManager();
                      for (PsiFile file : fileManager.getAllCachedFiles()) {
                        if (file.getLanguage() == langToReset) {
                          VirtualFile vf = OCInclusionContextUtil.getVirtualFile(file);
                          if (vf != null) {
                            fileManager.setViewProvider(vf, null);
                          }
                        }
                      }
                    }));
  }

  private static void rebuildSymbols(Project project, OCWorkspace workspace) {
    TransactionGuard.submitTransaction(
        project,
        () ->
            ApplicationManager.getApplication()
                .runReadAction(
                    () ->
                        OCWorkspaceModificationTrackersCompatUtils.getTrackers(project)
                            .getBuildSettingsChangesTracker()
                            .incModificationCount()));
  }
}
