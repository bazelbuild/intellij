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
package com.google.idea.blaze.clwb.oclang.run;

import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration;
import com.google.idea.blaze.clwb.run.RunConfigurationUtils;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider;
import com.jetbrains.cidr.execution.debugger.OCDebuggerLanguageSupport;
import javax.annotation.Nullable;

/**
 * A version of {@link OCDebuggerLanguageSupport} which can accept {@link
 * BlazeCommandRunConfiguration} when appropriate.
 */
public class BlazeCidrDebuggerSupportFactory extends OCDebuggerLanguageSupport {
  @Nullable
  @Override
  public XDebuggerEditorsProvider createEditor(RunProfile profile) {
    if (profile instanceof BlazeCommandRunConfiguration
        && RunConfigurationUtils.canUseClionRunner((BlazeCommandRunConfiguration) profile)) {
      return createEditorProvider();
    }
    return null;
  }
}
