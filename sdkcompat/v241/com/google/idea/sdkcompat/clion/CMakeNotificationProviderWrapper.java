/*
 * Copyright 2023 The Bazel Authors. All rights reserved.
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

package com.google.idea.sdkcompat.clion;


import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.cidr.cpp.cmake.workspace.CMakeNotificationProvider;

import java.util.function.Function;
import javax.annotation.Nullable;
import javax.swing.*;

// #api223
public class CMakeNotificationProviderWrapper {
    CMakeNotificationProvider value;

    public CMakeNotificationProviderWrapper(){
        this.value = new CMakeNotificationProvider();
    }

    @Nullable
    public JComponent createNotificationPanel(VirtualFile virtualFile, FileEditor fileEditor, Project project) {
        Function<? super FileEditor, ? extends JComponent> notificationProducer =
            this.value.collectNotificationData(project, virtualFile);

        if (notificationProducer != null) {
            return notificationProducer.apply(fileEditor);
        }

        return null;
    }
}
