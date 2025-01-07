/*
 * Copyright 2024 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.sync.aspects.storage;

import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.concurrency.ThreadingAssertions;
import java.util.Optional;

public class AspectRepositoryProvider {

  private static final String ASPECT_DIRECTORY = "aspect/default";

  private static final String ASPECT_TEMPLATE_DIRECTORY = "aspect/template";

  private static Optional<VirtualFile> findAspectDirectory(String directory) {
    final var classLoader = AspectRepositoryProvider.class.getClassLoader();

    final var file = Optional.ofNullable(classLoader.getResource(directory))
        .flatMap(it -> Optional.ofNullable(VfsUtil.findFileByURL(it)));

    ThreadingAssertions.assertNoReadAccess();
    file.ifPresent(it -> it.refresh(false, true));

    return file;
  }

  public static Optional<VirtualFile> aspectDirectory() {
    return findAspectDirectory(ASPECT_DIRECTORY);
  }

  public static Optional<VirtualFile> aspectTemplateDirectory() {
    return findAspectDirectory(ASPECT_TEMPLATE_DIRECTORY);
  }
}
