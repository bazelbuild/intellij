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
package com.google.idea.sdkcompat.openapi;

import com.intellij.openapi.vfs.VirtualFileSystem;
import com.intellij.openapi.vfs.impl.VirtualFileManagerImpl;
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent;
import com.intellij.util.messages.MessageBus;
import java.util.List;
import org.jetbrains.annotations.NotNull;

/** Compatibility layer for {@link VFileCreateEvent}. #api191 */
public class VirtualFileManagerCompat extends VirtualFileManagerImpl {
  public VirtualFileManagerCompat(List<VirtualFileSystem> fileSystems, @NotNull MessageBus bus) {
    super(fileSystems, bus);
  }
}
