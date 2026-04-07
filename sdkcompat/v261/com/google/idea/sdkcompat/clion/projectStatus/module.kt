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
