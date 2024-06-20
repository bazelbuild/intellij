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
package com.google.idea.blaze.cpp;

import com.google.idea.blaze.base.command.BlazeCommandName;
import com.google.idea.blaze.base.command.BlazeInvocationContext;
import com.google.idea.blaze.base.command.BuildFlagsProvider;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.registry.Registry;
import java.util.List;

public class CppAspectArgsProvider implements BuildFlagsProvider {
  private static final String REGISTRY_PREFIX = "bazel.cpp.aspect.";
  private static final String CPP_USE_GET_TOOL_FOR_ACTION = "_cpp_use_get_tool_for_action";

  @Override
  public void addBuildFlags(
      Project project,
      ProjectViewSet projectViewSet,
      BlazeCommandName command,
      BlazeInvocationContext invocationContext,
      List<String> flags) {
    // aspects are only used during sync
  }

  @Override
  public void addSyncFlags(
      Project project,
      ProjectViewSet projectViewSet,
      BlazeCommandName command,
      BlazeContext context,
      BlazeInvocationContext invocationContext,
      List<String> flags) {
    flags.add(String.format(
        "--define=%s=%b", CPP_USE_GET_TOOL_FOR_ACTION,
        Registry.is(REGISTRY_PREFIX + CPP_USE_GET_TOOL_FOR_ACTION)
    ));
  }
}
