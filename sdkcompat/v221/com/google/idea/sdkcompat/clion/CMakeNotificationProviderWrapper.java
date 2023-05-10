package com.google.idea.sdkcompat.cpp;

import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.cidr.cpp.cmake.workspace.CMakeNotificationProvider;

import javax.swing.*;

// #api223
public class CMakeNotificationProviderWrapper {
    CMakeNotificationProvider value;

    public CMakeNotificationProviderWrapper() {
        value = new CMakeNotificationProvider();
    }

    public JComponent createNotificationPanel(VirtualFile virtualFile, FileEditor fileEditor, Project project) {
        return value.createNotificationPanel(virtualFile, fileEditor, project);
    }
}
