/*
 * Copyright 2020 The Bazel Authors. All rights reserved.
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
package com.google.idea.testing.cidr;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.cidr.lang.toolchains.CidrFileSeparators;
import com.jetbrains.cidr.lang.workspace.OCResolveConfiguration;
import org.jetbrains.annotations.NotNull;

/**
 * Stub {@link OCResolveConfiguration} for testing.
 *
 * <p>#api201
 */
public class StubOCResolveConfiguration extends StubOCResolveConfigurationBase {
  public StubOCResolveConfiguration(Project project) {
    super(project);
  }

  @Override
  public CidrFileSeparators getFileSeparators() {
    return CidrFileSeparators.UNIX;
  }

  @Override
  public boolean hasSourceFile(@NotNull VirtualFile virtualFile) {
    return false;
  }
}
