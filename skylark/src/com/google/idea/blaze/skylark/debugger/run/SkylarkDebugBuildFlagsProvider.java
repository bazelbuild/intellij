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
package com.google.idea.blaze.skylark.debugger.run;

import com.google.idea.blaze.base.command.BlazeCommandName;
import com.google.idea.blaze.base.command.BuildFlagsProvider;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.run.ExecutorType;
import com.google.idea.blaze.skylark.debugger.SkylarkDebuggingUtils;
import com.intellij.openapi.project.Project;
import java.util.List;
import javax.annotation.Nullable;

class SkylarkDebugBuildFlagsProvider implements BuildFlagsProvider {

  private static final String ENABLE_DEBUGGING_FLAG = "--experimental_skylark_debug=true";
  private static final String SERVER_PORT_FLAG = "--experimental_debug_server_port";

  // TODO(brendandouglas): allow users to customize port?
  static final int SERVER_PORT = 7300;

  @Override
  public void addBuildFlags(
      Project project,
      ProjectViewSet projectViewSet,
      BlazeCommandName command,
      @Nullable ExecutorType executorType,
      List<String> flags) {
    if (!SkylarkDebuggingUtils.debuggingEnabled(project)) {
      return;
    }
    if (executorType == ExecutorType.DEBUG && command == BlazeCommandName.BUILD) {
      flags.add(ENABLE_DEBUGGING_FLAG);
      flags.add(SERVER_PORT_FLAG + "=" + SERVER_PORT);
    }
  }
}
