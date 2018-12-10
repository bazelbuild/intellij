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
import com.google.idea.blaze.cpp.CppBlazeRules;

/** Utility methods for CLion run configurations */
public class RunConfigurationUtils {

  static boolean canUseClionHandler(Kind kind) {
    return kind == CppBlazeRules.RuleTypes.CC_TEST.getKind()
        || kind == CppBlazeRules.RuleTypes.CC_BINARY.getKind();
  }

  static boolean canUseClionRunner(BlazeCommandRunConfiguration config) {
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
                && command.equals(BlazeCommandName.RUN)));
  }
}
