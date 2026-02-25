/*
 * Copyright 2026 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.execlog

import com.google.idea.blaze.base.execlog.prototext.ProtoTextLanguage
import com.intellij.openapi.fileTypes.LanguageFileType
import javax.swing.Icon

object ExeclogFileType : LanguageFileType(ProtoTextLanguage) {
  override fun getName(): String = "Execlog"

  override fun getDescription(): String = "Bazel execution log"

  override fun getDefaultExtension(): String = ""

  override fun getIcon(): Icon? = null
}
