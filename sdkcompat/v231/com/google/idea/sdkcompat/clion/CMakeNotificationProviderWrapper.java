package com.google.idea.sdkcompat.clion;


import com.intellij.openapi.extensions.ExtensionPoint;
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

    public static <T> void unregisterDelegateExtension(ExtensionPoint<T> extensionPoint) {
        for (T extension : extensionPoint.getExtensions()) {
            if (extension instanceof CMakeNotificationProvider) {
                extensionPoint.unregisterExtension(extension);
            }
        }
    }
}
