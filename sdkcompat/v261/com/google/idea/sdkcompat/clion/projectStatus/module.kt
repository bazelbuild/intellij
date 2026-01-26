package com.google.idea.sdkcompat.clion.projectStatus

import com.intellij.clion.projectStatus.convertStatus
import com.intellij.clion.projectStatus.isProjectAwareFile

typealias EditorNotificationWarningProvider = com.intellij.clion.projectStatus.notifications.EditorNotificationWarningProvider 
typealias ProjectNotification = com.intellij.clion.projectStatus.notifications.ProjectNotification 

typealias ProjectFixesProvider = com.intellij.clion.projectStatus.popup.ProjectFixesProvider 

typealias WidgetStatus = com.intellij.clion.projectStatus.widget.WidgetStatus
typealias Status = com.intellij.clion.projectStatus.widget.Status 
typealias Scope = com.intellij.clion.projectStatus.widget.Scope 
typealias DefaultWidgetStatus = com.intellij.clion.projectStatus.widget.DefaultWidgetStatus 
typealias WidgetStatusProvider = com.intellij.clion.projectStatus.widget.WidgetStatusProvider 

val convertStatus = ::convertStatus
val isProjectAwareFile = ::isProjectAwareFile
