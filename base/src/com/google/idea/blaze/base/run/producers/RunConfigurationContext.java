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
package com.google.idea.blaze.base.run.producers;

import com.google.idea.blaze.base.command.BlazeCommandName;
import com.google.idea.blaze.base.model.primitives.TargetExpression;
import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration;
import com.google.idea.blaze.base.run.state.BlazeCommandRunConfigurationCommonState;
import com.intellij.psi.PsiElement;
import java.util.Objects;

/** A context used to configure a blaze run configuration, possibly asynchronously. */
public interface RunConfigurationContext {

  /** The {@link PsiElement} most relevant to this context (e.g. a method, class, file, etc.). */
  PsiElement getSourceElement();

  /** Returns true if the run configuration was successfully configured. */
  boolean setupRunConfiguration(BlazeCommandRunConfiguration config);

  /** Returns true if the run configuration matches this {@link RunConfigurationContext}. */
  boolean matchesRunConfiguration(BlazeCommandRunConfiguration config);

  static RunConfigurationContext fromKnownTarget(
      TargetExpression target, BlazeCommandName command, PsiElement sourceElement) {
    return new RunConfigurationContext() {
      @Override
      public PsiElement getSourceElement() {
        return sourceElement;
      }

      @Override
      public boolean setupRunConfiguration(BlazeCommandRunConfiguration config) {
        config.setTarget(target);
        BlazeCommandRunConfigurationCommonState handlerState =
            config.getHandlerStateIfType(BlazeCommandRunConfigurationCommonState.class);
        if (handlerState == null) {
          return false;
        }
        handlerState.getCommandState().setCommand(command);
        config.setGeneratedName();
        return true;
      }

      @Override
      public boolean matchesRunConfiguration(BlazeCommandRunConfiguration config) {
        BlazeCommandRunConfigurationCommonState handlerState =
            config.getHandlerStateIfType(BlazeCommandRunConfigurationCommonState.class);
        if (handlerState == null) {
          return false;
        }
        return Objects.equals(handlerState.getCommandState().getCommand(), command)
            && Objects.equals(config.getTarget(), target)
            && handlerState.getTestFilter() == null;
      }
    };
  }
}
