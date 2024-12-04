package com.google.idea.blaze.base.qsync;

import com.google.idea.blaze.common.Context;
import com.google.idea.blaze.qsync.QuerySyncProjectListener;
import com.google.idea.blaze.qsync.QuerySyncProjectSnapshot;
import com.intellij.codeInsight.daemon.impl.analysis.FileHighlightingSettingListener;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightingSettingsPerFile;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiManager;

public class QuerySyncHighlightingResetListener implements QuerySyncProjectListener {

    private final Project project;

    QuerySyncHighlightingResetListener(Project project) {
        this.project = project;
    }

    @Override
    public void onNewProjectSnapshot(Context<?> context, QuerySyncProjectSnapshot instance) {
        for (VirtualFile virtualFile : FileEditorManager.getInstance(project).getOpenFiles()) {
            ApplicationManager.getApplication().invokeLater(() -> WriteAction.run(() -> {
                        var psiFile = PsiManager.getInstance(project).findFile(virtualFile);
                        if (psiFile != null) {
                            var newHighlighting = HighlightingSettingsPerFile
                                    .getInstance(project)
                                    .getHighlightingSettingForRoot(psiFile);
                            project.getMessageBus()
                                    .syncPublisher(FileHighlightingSettingListener.SETTING_CHANGE)
                                    .settingChanged(psiFile, newHighlighting);
                        }
                    }
            ));
        }
    }

    public static class Provider implements QuerySyncProjectListenerProvider {
        @Override
        public QuerySyncProjectListener createListener(QuerySyncProject querySyncProject) {
            return new QuerySyncHighlightingResetListener(querySyncProject.getIdeProject());
        }
    }
}

