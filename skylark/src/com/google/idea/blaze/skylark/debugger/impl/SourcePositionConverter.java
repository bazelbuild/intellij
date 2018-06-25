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
package com.google.idea.blaze.skylark.debugger.impl;

import com.google.devtools.build.lib.skylarkdebugging.SkylarkDebuggingProtos.Location;
import com.google.idea.blaze.base.io.VfsUtils;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.xdebugger.XDebuggerUtil;
import com.intellij.xdebugger.XSourcePosition;
import java.io.File;
import javax.annotation.Nullable;

/** Converts between source location formats used by the IDE and the Skylark debug server. */
class SourcePositionConverter {

  @Nullable
  static XSourcePosition fromLocationProto(Location location) {
    VirtualFile vf = VfsUtils.resolveVirtualFile(new File(location.getPath()));
    if (vf == null) {
      return null;
    }
    return XDebuggerUtil.getInstance().createPosition(vf, location.getLineNumber() - 1);
  }
}
