/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.clwb.run;

import com.google.idea.blaze.base.command.BlazeCommandName;
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration;
import com.google.idea.blaze.base.run.state.BlazeCommandRunConfigurationCommonState;
import com.google.idea.blaze.cpp.BlazeCTargetInfoService;
import com.google.idea.blaze.cpp.CppBlazeRules;
import com.intellij.openapi.util.registry.Registry;
import com.jetbrains.cidr.lang.CLanguageKind;
import com.jetbrains.cidr.lang.workspace.OCWorkspace;
import com.jetbrains.cidr.lang.workspace.compiler.OCCompilerKind;
import com.jetbrains.cidr.lang.workspace.compiler.UnknownCompilerKind;
import javax.annotation.Nullable;

/** Utility methods for CLion run configurations */
public class RunConfigurationUtils {

  static boolean canUseClionHandler(@Nullable Kind kind) {
    return kind == CppBlazeRules.RuleTypes.CC_TEST.getKind()
        || kind == CppBlazeRules.RuleTypes.CC_BINARY.getKind();
  }

  public static boolean canUseClionRunner(BlazeCommandRunConfiguration config) {
    Kind kind = config.getTargetKind();
    BlazeCommandRunConfigurationCommonState handlerState =
        config.getHandlerStateIfType(BlazeCommandRunConfigurationCommonState.class);
    if (handlerState == null) {
      return false;
    }
    BlazeCommandName command = handlerState.getCommandState().getCommand();
    return kind != null
        && command != null
        && ((kind == CppBlazeRules.RuleTypes.CC_TEST.getKind()
                && command.equals(BlazeCommandName.TEST))
            || (kind == CppBlazeRules.RuleTypes.CC_BINARY.getKind()
                && command.equals(BlazeCommandName.RUN))) ;
  }

  public static OCCompilerKind getCompilerKind(BlazeCommandRunConfiguration runConfig) {
    final var project = runConfig.getProject();

    final var info = BlazeCTargetInfoService.getFirst(project, runConfig.getTargets());
    if (info == null) {
      return UnknownCompilerKind.INSTANCE;
    }

    final var resolveConfig = OCWorkspace.getInstance(project).getConfigurationById(info.getConfigurationId());
    if (resolveConfig == null) {
      return UnknownCompilerKind.INSTANCE;
    }

    return resolveConfig.getCompilerSettings(CLanguageKind.CPP).getCompilerKind();
  }

  public static BlazeDebuggerKind getDebuggerKind(BlazeCommandRunConfiguration runConfig) {
    if (Registry.is("bazel.clwb.debug.use.default.toolchain")) {
      return BlazeDebuggerKind.byDefaultToolchain();
    } else {
      return BlazeDebuggerKind.byHeuristic(getCompilerKind(runConfig));
    }
  }
}
