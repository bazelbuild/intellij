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
import com.goide.sdk.GoSdkService;
import com.google.common.collect.Maps;
import com.google.idea.blaze.base.io.VfsUtils;
import com.google.idea.blaze.base.model.primitives.ExecutionRootPath;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.sync.workspace.ExecutionRootPathResolver;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;

class BlazeDlvPositionConverter implements DlvPositionConverter {
  private static final Logger logger = Logger.getInstance(BlazeDlvPositionConverter.class);

  private final WorkspaceRoot root;
  private final String goRoot;
  private final ExecutionRootPathResolver resolver;
  private final Map<VirtualFile, String> localToRemote;
  private final Map<String, VirtualFile> normalizedToLocal;

  private BlazeDlvPositionConverter(
      WorkspaceRoot workspaceRoot,
      String goRoot,
      ExecutionRootPathResolver resolver,
      Set<String> remotePaths) {
    this.root = workspaceRoot;
    this.goRoot = goRoot;
    this.resolver = resolver;
    this.localToRemote = Maps.newHashMapWithExpectedSize(remotePaths.size());
    this.normalizedToLocal = Maps.newHashMapWithExpectedSize(remotePaths.size());
    for (String path : remotePaths) {
      String normalized = normalizePath(path);
      if (normalizedToLocal.containsKey(normalized)) {
        continue;
      }
      VirtualFile localFile = resolve(normalized);
      if (localFile != null) {
        if (remotePaths.contains(normalized)) {
          localToRemote.put(localFile, normalized);
        } else {
          localToRemote.put(localFile, path);
        }
        normalizedToLocal.put(normalized, localFile);
      } else {
        logger.warn("Unable to find local file for debug path: " + path);
      }
    }
  }

  @Nullable
  @Override
  public String toRemotePath(VirtualFile localFile) {
    String remotePath = localToRemote.get(localFile);
    if (remotePath != null) {
      return remotePath;
    }
    remotePath =
        root.isInWorkspace(localFile)
            ? root.workspacePathFor(localFile).relativePath()
            : localFile.getPath();
    localToRemote.put(localFile, remotePath);
    return remotePath;
  }

  @Nullable
  @Override
  public VirtualFile toLocalFile(String remotePath) {
    String normalized = normalizePath(remotePath);
    VirtualFile localFile = normalizedToLocal.get(normalized);
    if (localFile == null || !localFile.isValid()) {
      localFile = resolve(normalized);
      if (localFile != null) {
        normalizedToLocal.put(normalized, localFile);
      }
    }
    return localFile;
  }

  @Nullable
  private VirtualFile resolve(String normalizedPath) {
    return VfsUtils.resolveVirtualFile(
        resolver.resolveExecutionRootPath(new ExecutionRootPath(normalizedPath)),
        /* refreshIfNeeded= */ false);
  }

  private String normalizePath(String path) {
    if (path.startsWith("/build/work/")) {
      // /build/work/<hash>/<project>/actual/path
      return afterNthSlash(path, 5);
    } else if (path.startsWith("/tmp/go-build-release/buildroot/")) {
      return afterNthSlash(path, 4);
    } else if (path.startsWith("GOROOT/")) {
      return goRoot + '/' + afterNthSlash(path, 1);
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
      WorkspaceRoot workspaceRoot = WorkspaceRoot.fromProjectSafe(project);
      String goRoot = GoSdkService.getInstance(project).getSdk(module).getHomePath();
      ExecutionRootPathResolver resolver = ExecutionRootPathResolver.fromProject(project);
      return (workspaceRoot != null && resolver != null)
          ? new BlazeDlvPositionConverter(workspaceRoot, goRoot, resolver, remotePaths)
          : null;
    }
  }
}
