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
package com.google.idea.blaze.base.projectview.section.sections;

import com.google.idea.blaze.base.lang.projectview.psi.ProjectViewPsiListItem;
import com.google.idea.blaze.base.lang.projectview.psi.ProjectViewPsiListSection;
import com.google.idea.blaze.base.model.primitives.TargetExpression;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.projectview.ProjectView;
import com.google.idea.blaze.base.projectview.ProjectViewEdit;
import com.google.idea.blaze.base.projectview.section.ListSection;
import com.google.idea.blaze.base.settings.ui.AddDirectoryToProjectAction;
import com.google.idea.common.experiments.BoolExperiment;
import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.LineMarkerProvider;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.List;

/**
 * A line marker provider for project view files, showing a plus icon for adding new directories.
 */
public class DirectoryLineMarkerProvider implements LineMarkerProvider {

    private static final BoolExperiment enabled = new BoolExperiment("projectview.directory.section.gutter.icons.enabled", true);

    @Nullable
    @Override
    @SuppressWarnings("rawtypes")
    public LineMarkerInfo getLineMarkerInfo(@NotNull PsiElement element) {
        if (enabled.getValue() && element instanceof LeafPsiElement leafPsiElement) {
            if (element.getText().equals("directories") &&
                    leafPsiElement.getParent() instanceof ProjectViewPsiListSection) {
                return new LineMarkerInfo<>(
                        element,
                        element.getTextRange(),
                        AllIcons.Actions.AddFile,
                        psi -> "Add Directory to Project",
                        (e, elt) -> AddDirectoryToProjectAction.runAction(element.getProject(), null),
                        GutterIconRenderer.Alignment.RIGHT,
                        () -> "Add Directory to Project");
            } else if (element.getParent() instanceof ProjectViewPsiListItem parent) {
                var parentTag = parent.getParent().getFirstChild().getText();

                if (parentTag.equals("directories") || parentTag.equals("targets")) {
                    var disabled = element.getText().startsWith("-");
                    var txt = disabled ? "Enable" : "Disable";
                    var icon = disabled ? AllIcons.Diff.GutterCheckBox : AllIcons.Diff.GutterCheckBoxSelected;

                    return new LineMarkerInfo<>(
                            element,
                            element.getTextRange(),
                            icon,
                            psi -> txt,
                            (e, elt) -> toggleTarget(elt, disabled, parentTag),
                            GutterIconRenderer.Alignment.RIGHT,
                            () -> txt);
                }
            }
        }

        return null;
    }

    @Override
    public void collectSlowLineMarkers(@NotNull List<? extends PsiElement> elements, @NotNull Collection<? super LineMarkerInfo<?>> result) {
    }

    private void toggleTarget(PsiElement elt, boolean disabled, String parentTag) {
        ProjectViewEdit.ProjectViewEditor action = switch (parentTag) {
            case "directories" -> (builder) -> toggleDirectory(builder, elt, disabled);
            case "targets" -> (builder) -> toggleTarget(builder, elt, disabled);
            default -> null;
        };

        if (action == null) {
            Messages.showErrorDialog(
                    "Could not modify project view: invalid tag %s.".formatted(parentTag),
                    "Error");
            return;
        }

        var edit = ProjectViewEdit.editLocalProjectView(elt.getProject(), action);

        if (edit == null) {
            Messages.showErrorDialog(
                    "Could not modify project view. Check for errors in your project view and try again",
                    "Error");
            return;
        }

        edit.apply();
    }

    private static boolean toggleDirectory(ProjectView.Builder builder, PsiElement elt, boolean disabled) {
        var directories = builder.getLast(DirectorySection.KEY);
        var directoriesUpdater = ListSection.update(DirectorySection.KEY, directories);

        var directoryStr = elt.getText().substring(disabled ? 1 : 0);

        directoriesUpdater.replaceElement(
                disabled ?
                        DirectoryEntry.exclude(new WorkspacePath(directoryStr)) :
                        DirectoryEntry.include(new WorkspacePath(directoryStr)),
                disabled ?
                        DirectoryEntry.include(new WorkspacePath(directoryStr)) :
                        DirectoryEntry.exclude(new WorkspacePath(directoryStr))
        );

        builder.replace(directories, directoriesUpdater);

        return true;
    }

    private static boolean toggleTarget(ProjectView.Builder builder, PsiElement elt, boolean disabled) {
        var targets = builder.getLast(TargetSection.KEY);
        var targetsUpdater = ListSection.update(TargetSection.KEY, targets);

        var targetStr = elt.getText();

        targetsUpdater.replaceElement(
                TargetExpression.fromStringSafe(targetStr),
                disabled ?
                        TargetExpression.fromStringSafe(targetStr.substring(1)) :
                        TargetExpression.fromStringSafe('-' + targetStr)
        );

        builder.replace(targets, targetsUpdater);

        return true;
    }
}
