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

package com.google.idea.blaze.clwb;

import com.google.idea.blaze.base.wizard2.BazelNotificationProvider;
import com.intellij.openapi.vfs.VirtualFile;

import com.jetbrains.cidr.lang.OCLanguageUtilsBase;
import org.jetbrains.annotations.NotNull;

/**
 * Provide notification for C-family files temporarily until moving to new CLion project status
 * api.
 */
// #api241
public class BazelCNotificationProvider extends BazelNotificationProvider {

  @Override
  protected boolean isProjectAwareFile(@NotNull VirtualFile file) {
    return OCLanguageUtilsBase.isSupported(file.getFileType());
  }
}