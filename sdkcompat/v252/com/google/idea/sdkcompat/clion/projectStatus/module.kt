/*
 * Copyright 2026 The Bazel Authors. All rights reserved.
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
