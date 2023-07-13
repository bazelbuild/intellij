package com.google.idea.blaze.base.project;

import com.google.idea.blaze.base.settings.ui.OpenProjectViewAction;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.util.registry.Registry;
import org.jetbrains.annotations.NotNull;

public class OpenProjectViewStartupActivity implements StartupActivity, DumbAware {
    @Override
    public void runActivity(@NotNull Project project) {
        if (Boolean.TRUE.equals(project.getUserData(AutoImportProjectOpenProcessor.PROJECT_AUTO_IMPORTED)) &&
                Registry.is("blaze.project.import.open_project_view")) {
            OpenProjectViewAction.openLocalProjectViewFile(project);
        }
    }
}
