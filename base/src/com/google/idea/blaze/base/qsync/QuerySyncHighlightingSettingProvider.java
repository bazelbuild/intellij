package com.google.idea.blaze.base.qsync;

import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.BlazeImportSettings;
import com.intellij.codeInsight.daemon.impl.analysis.DefaultHighlightingSettingProvider;
import com.intellij.codeInsight.daemon.impl.analysis.FileHighlightingSetting;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class QuerySyncHighlightingSettingProvider extends DefaultHighlightingSettingProvider {
    @Override
    public @Nullable FileHighlightingSetting getDefaultSetting(@NotNull Project project, @NotNull VirtualFile file) {
        var psiFile = PsiManager.getInstance(project).findFile(file);
        if (Blaze.getProjectType(psiFile.getProject()) == BlazeImportSettings.ProjectType.QUERY_SYNC) {
            if(!QuerySyncManager.getInstance(psiFile.getProject()).isReadyForAnalysis(psiFile)){
                return FileHighlightingSetting.ESSENTIAL;
            }
        }
        return null;

    }
}
