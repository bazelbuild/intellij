/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.golang.resolve;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import java.io.File;
import java.util.Collection;
import java.util.concurrent.ConcurrentMap;
import javax.annotation.Nullable;

/**
 * Represents a virtual directory component under the virtual go root. Used to resolve path
 * components in import statements.
 */
public class BlazeVirtualGoDirectory extends BlazeVirtualDirectory {
  private final String path;
  @Nullable private final BlazeVirtualGoDirectory parent;
  private final ConcurrentMap<String, BlazeVirtualGoDirectory> children = Maps.newConcurrentMap();

  private BlazeVirtualGoDirectory() {
    super("");
    this.path = "";
    this.parent = null;
  }

  static BlazeVirtualGoDirectory createRoot() {
    return new BlazeVirtualGoDirectory();
  }

  BlazeVirtualGoDirectory(String name, BlazeVirtualGoDirectory parent) {
    super(name);
    this.path = parent.getPath() + "/" + name;
    this.parent = parent;
    parent.addChild(this);
  }

  static BlazeVirtualGoDirectory getInstance(Project project, String path) {
    if (Strings.isNullOrEmpty(path)) {
      return BlazeGoRootsProvider.getGoPathSourceRoot(project);
    }
    File file = new File(path);
    BlazeVirtualGoDirectory parent = getInstance(project, file.getParent());
    String name = file.getName();
    BlazeVirtualGoDirectory instance = parent.findSubdirectory(name);
    return instance == null ? new BlazeVirtualGoDirectory(name, parent) : instance;
  }

  @Override
  public String getPath() {
    return path;
  }

  @Override
  @Nullable
  public NewVirtualFile getParent() {
    return parent;
  }

  @Override
  public NewVirtualFile[] getChildren() {
    return children.values().toArray(new NewVirtualFile[0]);
  }

  private void addChild(BlazeVirtualGoDirectory child) {
    children.put(child.getName(), child);
  }

  private BlazeVirtualGoDirectory findSubdirectory(String name) {
    return children.get(name);
  }

  @Nullable
  @Override
  public NewVirtualFile findChild(String name) {
    return findSubdirectory(name);
  }

  @Override
  public Collection<VirtualFile> getCachedChildren() {
    return ImmutableList.copyOf(children.values());
  }
}
