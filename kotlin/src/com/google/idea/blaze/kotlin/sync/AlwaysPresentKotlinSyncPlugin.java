/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.kotlin.sync;

import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.plugin.PluginUtils;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.output.IssueOutput;
import com.google.idea.blaze.base.sync.BlazeSyncPlugin;
import com.google.idea.blaze.base.sync.projectview.WorkspaceLanguageSettings;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Nullable;

/**
 * This plugin bootstraps Kotlin support.
 */
// This class should not class load anything from the Kotlin plugin. This also means don't load anything from the packages in this plugin. This is why the
// PLUGIN_ID is hard coded below.
public class AlwaysPresentKotlinSyncPlugin implements BlazeSyncPlugin {
    private static final String PLUGIN_ID = "org.jetbrains.kotlin";

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
