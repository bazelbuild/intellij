package com.google.idea.blaze.kotlin.sync;

import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.plugin.PluginUtils;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.output.IssueOutput;
import com.google.idea.blaze.base.sync.projectview.WorkspaceLanguageSettings;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Nullable;

import static com.google.idea.blaze.kotlin.BlazeKotlin.PLUGIN_ID;

/**
 * This plugin bootstraps Kotlin support.
 */
public class AlwaysPresentKotlinSyncPlugin extends BlazeKotlinBaseSyncPlugin {
    @Override
    public boolean validateProjectView(
            @Nullable Project project,
            BlazeContext context,
            ProjectViewSet projectViewSet,
            WorkspaceLanguageSettings workspaceLanguageSettings) {
        if (!workspaceLanguageSettings.isLanguageActive(LanguageClass.KOTLIN)) {
            return true;
        }
        if (!PluginUtils.isPluginEnabled(PLUGIN_ID)) {
            IssueOutput.error("The Kotlin plugin is required for Kotlin support. Click here to install/enable the plugin and restart")
                    .navigatable(PluginUtils.installOrEnablePluginNavigable(PLUGIN_ID))
                    .submit(context);
            return false;
        }
        return true;
    }
}
