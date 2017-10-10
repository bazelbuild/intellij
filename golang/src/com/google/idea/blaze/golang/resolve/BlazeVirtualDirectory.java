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

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.ex.dummy.DummyFileIdGenerator;
import com.intellij.openapi.vfs.ex.temp.TempFileSystem;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import com.intellij.openapi.vfs.newvfs.NewVirtualFileSystem;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Collection;
import javax.annotation.Nullable;

/** A fake {@link NewVirtualFile} stub. */
public abstract class BlazeVirtualDirectory extends NewVirtualFile {
  protected final String name;
  private final int id = DummyFileIdGenerator.next();

  BlazeVirtualDirectory(String name) {
    this.name = name;
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public NewVirtualFileSystem getFileSystem() {
    return TempFileSystem.getInstance();
  }

  @Override
  public boolean isWritable() {
    return false;
  }

  @Override
  public boolean isDirectory() {
    return true;
  }

  @Override
  public boolean exists() {
    return false;
  }

  @Override
  public boolean isValid() {
    return true;
  }

  @Override
  public long getTimeStamp() {
    return -1L;
  }

  @Override
  public long getLength() {
    return 0L;
  }

  @Override
  public void refresh(boolean asynchronous, boolean recursive, @Nullable Runnable postRunnable) {}

  @Override
  public InputStream getInputStream() throws IOException {
    throw new IOException();
  }

  @Override
  public OutputStream getOutputStream(
      Object requestor, long newModificationStamp, long newTimeStamp) throws IOException {
    throw new IOException();
  }

  // From NewVirtualFile

  @Override
  public int getId() {
    return id;
  }

  @Override
  public CharSequence getNameSequence() {
    return name;
  }

  @Nullable
  @Override
  public BlazeVirtualDirectory getCanonicalFile() {
    return this;
  }

  @Nullable
  @Override
  public NewVirtualFile refreshAndFindChild(String name) {
    return findChild(name);
  }

  @Nullable
  @Override
  public NewVirtualFile findChildIfCached(String name) {
    return findChild(name);
  }

  @Override
  public Collection<VirtualFile> getCachedChildren() {
    return Arrays.asList(getChildren());
  }

  @Override
  public Iterable<VirtualFile> iterInDbChildren() {
    return getCachedChildren();
  }

  @Override
  public void setTimeStamp(long time) throws IOException {
    throw new IOException();
  }

  @Override
  public void setWritable(boolean writable) throws IOException {
    throw new IOException();
  }

  @Override
  public void markDirty() {}

  @Override
  public void markDirtyRecursively() {}

  @Override
  public boolean isDirty() {
    return false;
  }

  @Override
  public void markClean() {}
}
