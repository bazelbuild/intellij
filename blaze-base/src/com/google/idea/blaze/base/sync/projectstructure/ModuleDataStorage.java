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
package com.google.idea.blaze.base.sync.projectstructure;

/**
 * Constants about where we store module data.
 */
public class ModuleDataStorage {
  public static final String MODULE_DATA_SUBDIRECTORY = "modules";
  public static final String DATA_SUBDIRECTORY = ".blaze";
  public static final String WORKSPACE_MODULE_NAME = ".workspace";
  public static final String PROJECT_DATA_DIR_MODULE_NAME = ".project-data-dir";
}
