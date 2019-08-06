/*
 * Copyright 2019 The Bazel Authors. All rights reserved.
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
package com.google.idea.sdkcompat.openapi;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileTypes.impl.FileTypeManagerImpl;
import com.intellij.openapi.options.SchemeManagerFactory;

/** Adapter to bridge different SDK versions. #api191 */
public class FileTypeManagerImplAdapter extends FileTypeManagerImpl {

  public FileTypeManagerImplAdapter() {
    super(
        ApplicationManager.getApplication().getMessageBus(),
        SchemeManagerFactory.getInstance(),
        PropertiesComponent.getInstance());
  }
}
