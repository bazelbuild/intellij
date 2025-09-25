package com.google.idea.sdkcompat.clion.projectStatus

import com.jetbrains.cidr.project.ui.convertStatus
import com.jetbrains.cidr.project.ui.isProjectAwareFile

typealias EditorNotificationWarningProvider = com.jetbrains.cidr.project.ui.notifications.EditorNotificationWarningProvider
typealias ProjectNotification = com.jetbrains.cidr.project.ui.notifications.ProjectNotification

typealias ProjectFixesProvider = com.jetbrains.cidr.project.ui.popup.ProjectFixesProvider

typealias WidgetStatus = com.jetbrains.cidr.project.ui.widget.WidgetStatus
typealias Status = com.jetbrains.cidr.project.ui.widget.Status
typealias Scope = com.jetbrains.cidr.project.ui.widget.Scope
typealias DefaultWidgetStatus = com.jetbrains.cidr.project.ui.widget.DefaultWidgetStatus
typealias WidgetStatusProvider = com.jetbrains.cidr.project.ui.widget.WidgetStatusProvider

val convertStatus = ::convertStatus
val isProjectAwareFile = ::isProjectAwareFile 
