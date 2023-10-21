package com.google.idea.blaze.base.wizard2;

import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.project.AutoImportProjectOpenProcessor;
import com.google.idea.blaze.base.ui.UiUtil;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.ui.components.JBLabel;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public class UseKnownProjectViewOption implements BlazeSelectProjectViewOption {

    private final String optionName;
    private final String description;
    private final File projectView;
    private final JComponent component;

    private UseKnownProjectViewOption(String optionName, String description, File projectView, WorkspaceRoot workspaceRoot) {
        this.optionName = optionName;
        this.description = description;
        this.projectView = projectView;
        WorkspacePath workspacePath = workspaceRoot.workspacePathForSafe(projectView);
        this.component = UiUtil.createHorizontalBox(
                HORIZONTAL_LAYOUT_GAP, new JBLabel("Project view:"),
                new JBLabel(workspacePath != null ? workspacePath.relativePath() : projectView.getAbsolutePath()));
    }

    @Override
    public String getOptionName() {
        return optionName;
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Nullable
    @Override
    public String getInitialProjectViewText() {
        try {
            byte[] bytes = Files.readAllBytes(projectView.toPath());
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }

    @Override
    public void validateAndUpdateBuilder(BlazeNewProjectBuilder builder) throws ConfigurationException {

    }

    @Override
    public void commit() throws BlazeProjectCommitException {

    }

    @Nullable
    @Override
    public JComponent getUiComponent() {
        return component;
    }

    public static UseKnownProjectViewOption fromManagedProject(WorkspaceRoot root) {
        return new UseKnownProjectViewOption("use-managed-view",
                "Clone project's default view",
                root.absolutePathFor(AutoImportProjectOpenProcessor.MANAGED_PROJECT_RELATIVE_PATH).toFile(),
                root);
    }

    public static UseKnownProjectViewOption fromEnvironmentVariable(WorkspaceRoot root, File file) {
        return new UseKnownProjectViewOption("use-view-from-env",
                "Clone project view provided from environment",
                file,
                root);
    }
}
