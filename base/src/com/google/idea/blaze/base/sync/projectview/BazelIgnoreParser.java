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
package com.google.idea.blaze.base.sync.projectview;


import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.io.VfsUtils;
import com.google.idea.blaze.base.io.VirtualFileSystemProvider;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vfs.VirtualFile;
import java.io.File;

/** A parser for .bazelgnore files, which tells Bazel a list of paths to ignore. */
public class BazelIgnoreParser {

  Logger logger = Logger.getInstance(BazelIgnoreParser.class);

  private final File bazelIgnoreFile;

  public BazelIgnoreParser(WorkspaceRoot workspaceRoot) {
    this.bazelIgnoreFile = workspaceRoot.fileForPath(new WorkspacePath(".bazelignore"));
  }

  /**
   * Parse a .bazelignore file (if it exists) for workspace relative paths.
   * @return a list of validated WorkspacePaths.
   */
  public ImmutableList<WorkspacePath> getIgnoredPaths() {
    if (VirtualFileSystemProvider.getInstance() == null) {
      return ImmutableList.of();
    }

    VirtualFile vf = VfsUtils.resolveVirtualFile(bazelIgnoreFile);
    if (vf == null || !vf.exists()) {
      return ImmutableList.of();
    }

    Document document = FileDocumentManager.getInstance().getDocument(vf);
    if (document == null || document.getImmutableCharSequence().length() == 0)  {
      return ImmutableList.of();
    }

    ImmutableList.Builder<WorkspacePath> ignoredPaths = ImmutableList.builder();

    for (CharSequence cs : Splitter.on("\n").split(document.getImmutableCharSequence())) {
      String path = cs.toString();
      String validationOutcome = WorkspacePath.validate(path);
      if (validationOutcome != null) {
        if (validationOutcome.contains("may not end with '/'")) {
          path = path.substring(0, path.length() - 1);
        } else {
          logger.warn(
              "Found "
                  + path
                  + " in .bazelignore, but unable to parse as relative workspace path for reason: "
                  + validationOutcome);
          continue;
        }
      }

      ignoredPaths.add(new WorkspacePath(path));
    }

    return ignoredPaths.build();
  }

}
