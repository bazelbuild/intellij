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
package com.google.idea.sdkcompat.cidr;

import com.intellij.openapi.vfs.VirtualFile;
import java.io.File;
import javax.annotation.Nullable;

/** Compat interface for {@link WorkspaceFileMapper}. */
public class OCWorkspaceFileMapperCompat {
  /** Present only for compatibility with v191, unused in v192. */
  public static WorkspaceFileMapper create() {
    return new WorkspaceFileMapper() {
      @Nullable
      @Override
      public VirtualFile map(File file) {
        return null;
      }
    };
  }
}
