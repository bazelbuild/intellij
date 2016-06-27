/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.io;

import com.intellij.openapi.components.ServiceManager;

import java.io.File;

/**
 * Simple file system checks (existence, isDirectory)
 */
public class FileAttributeProvider {

  public static FileAttributeProvider getInstance() {
    return ServiceManager.getService(FileAttributeProvider.class);
  }

  public boolean exists(File file) {
    return file.exists();
  }

  public boolean isDirectory(File file) {
    return file.isDirectory();
  }

  public boolean isFile(File file) {
    return file.isFile();
  }

  public long getFileModifiedTime(File file) {
    return file.lastModified();
  }

}
