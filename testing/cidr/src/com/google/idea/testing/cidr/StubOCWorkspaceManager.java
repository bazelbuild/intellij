package com.google.idea.testing.cidr;

import com.google.idea.sdkcompat.cidr.OCWorkspaceModificationTrackersCompatUtils;
import com.google.idea.sdkcompat.transactions.Transactions;
import com.intellij.lang.Language;
import com.intellij.openapi.application.ApplicationManager;
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
    Transactions.submitTransaction(
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
