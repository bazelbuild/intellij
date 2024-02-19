/*
 * Copyright 2024 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.idea.sdkcompat.general;

import com.intellij.ide.errorTreeView.NewErrorTreeViewPanel;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class NewErrorTreeViewPanelAdapter extends NewErrorTreeViewPanel {
    public NewErrorTreeViewPanelAdapter(@NotNull Project project, @Nullable String helpId, boolean createExitAction, boolean createToolbar, @Nullable Runnable rerunAction) {
        super(project, helpId, createExitAction, createToolbar, rerunAction);
    }

    /** #api233 */
    public Project getProject(){
        return project;
    }
}
