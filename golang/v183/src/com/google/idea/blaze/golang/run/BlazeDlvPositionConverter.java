/*
 * Copyright 2018 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.golang.run;

import com.goide.dlv.location.DlvPositionConverter;
import com.goide.dlv.location.DlvPositionConverterFactory;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.Maps;
import com.google.idea.blaze.base.io.VfsUtils;
import com.google.idea.blaze.base.model.primitives.ExecutionRootPath;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.sync.workspace.ExecutionRootPathResolver;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import java.util.Set;
import javax.annotation.Nullable;

class BlazeDlvPositionConverter implements DlvPositionConverter {
  private static final Logger logger = Logger.getInstance(BlazeDlvPositionConverter.class);

  private final WorkspaceRoot root;
  private final ExecutionRootPathResolver resolver;
  private final BiMap<String, VirtualFile> remoteToLocal;

  private BlazeDlvPositionConverter(
      WorkspaceRoot root, ExecutionRootPathResolver resolver, Set<String> remotePaths) {
    this.resolver = resolver;
    this.root = root;
    this.remoteToLocal = Maps.synchronizedBiMap(HashBiMap.create(remotePaths.size()));
    for (String path : remotePaths) {
      String normalizedPath = normalizePath(path);
      if (remoteToLocal.containsKey(normalizedPath)) {
        continue;
      }
      VirtualFile localFile = resolve(normalizedPath);
      if (localFile != null) {
        remoteToLocal.put(normalizedPath, localFile);
      } else {
        logger.warn("Unable to find local file for debug path: " + normalizedPath);
      }
    }
  }

  @Nullable
  @Override
  public String toRemotePath(VirtualFile localFile) {
    String remotePath = remoteToLocal.inverse().get(localFile);
    if (remotePath != null) {
      return remotePath;
    } else if (root.isInWorkspace(localFile)) {
      remotePath = root.workspacePathFor(localFile).relativePath();
      remoteToLocal.put(remotePath, localFile);
      return remotePath;
    }
    return localFile.getPath();
  }

  @Nullable
  @Override
  public VirtualFile toLocalFile(String remotePath) {
    String normalizedPath = normalizePath(remotePath);
    VirtualFile localFile = remoteToLocal.get(normalizedPath);
    if (localFile == null) {
      localFile = resolve(normalizedPath);
      if (localFile != null) {
        remoteToLocal.put(normalizedPath, localFile);
      }
    }
    return localFile;
  }

  @Nullable
  private VirtualFile resolve(String normalizedPath) {
    return VfsUtils.resolveVirtualFile(
        resolver.resolveExecutionRootPath(new ExecutionRootPath(normalizedPath)));
  }

  private static String normalizePath(String path) {
    if (path.startsWith("/build/work/")) {
      // /build/work/<hash>/<project>/actual/path
      return afterNthSlash(path, 5);
    } else if (path.startsWith("/tmp/go-build-release/buildroot/")) {
      return afterNthSlash(path, 4);
    }
    return path;
  }

  /**
   * @return path substring after nth slash, if path contains at least n slashes, return path
   *     unchanged otherwise.
   */
  private static String afterNthSlash(String path, int n) {
    int index = 0;
    for (int i = 0; i < n; ++i) {
      index = path.indexOf('/', index) + 1;
      if (index == 0) { // -1 + 1
        return path;
      }
    }
    return path.substring(index);
  }

  static class Factory implements DlvPositionConverterFactory {
    @Nullable
    @Override
    public DlvPositionConverter createPositionConverter(
        Project project, @Nullable Module module, Set<String> remotePaths) {
      WorkspaceRoot root = WorkspaceRoot.fromProjectSafe(project);
      ExecutionRootPathResolver resolver = ExecutionRootPathResolver.fromProject(project);
      return (root != null && resolver != null)
          ? new BlazeDlvPositionConverter(root, resolver, remotePaths)
          : null;
    }
  }
}
