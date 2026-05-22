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

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.idea.blaze.base.command.BlazeCommandName;
import com.google.idea.blaze.base.dependencies.TargetInfo;
import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration;
import com.google.idea.blaze.base.run.BlazeConfigurationNameBuilder;
import com.google.idea.blaze.base.run.ExecutorType;
import com.google.idea.blaze.base.run.state.BlazeCommandRunConfigurationCommonState;
import com.intellij.psi.PsiElement;
import java.util.Objects;
import javax.annotation.Nullable;

/** A context related to a blaze test target, used to configure a run configuration. */
public abstract class TestContext implements RunConfigurationContext {

  final PsiElement sourceElement;

  @Nullable
  final String testFilter;

  @Nullable
  final String description;

  TestContext(PsiElement sourceElement, @Nullable String testFilter, @Nullable String description) {
    this.sourceElement = sourceElement;
    this.testFilter = testFilter;
    this.description = description;
  }

  /** The {@link PsiElement} relevant to this test context (e.g. a method, class, file, etc.). */
  @Override
  public final PsiElement getSourceElement() {
    return sourceElement;
  }

  /** Returns true if the run configuration was successfully configured. */
  @Override
  public final boolean setupRunConfiguration(BlazeCommandRunConfiguration config) {
    if (!setupTarget(config)) {
      return false;
    }
    BlazeCommandRunConfigurationCommonState commonState =
        config.getHandlerStateIfType(BlazeCommandRunConfigurationCommonState.class);
    if (commonState == null) {
      return false;
    }
    commonState.getCommandState().setCommand(BlazeCommandName.TEST);
    commonState.getTestFilterState().setTestFilter(testFilter);

    if (description != null) {
      BlazeConfigurationNameBuilder nameBuilder = new BlazeConfigurationNameBuilder(config);
      nameBuilder.setTargetString(description);
      config.setName(nameBuilder.build());
      config.setNameChangedByUser(true); // don't revert to generated name
    } else {
      config.setGeneratedName();
    }
    return true;
  }

  /** Returns true if the run configuration matches this {@link TestContext}. */
  @Override
  public boolean matchesRunConfiguration(BlazeCommandRunConfiguration config) {
    BlazeCommandRunConfigurationCommonState commonState =
        config.getHandlerStateIfType(BlazeCommandRunConfigurationCommonState.class);
    if (commonState == null) {
      return false;
    }
    if (!Objects.equals(commonState.getCommandState().getCommand(), BlazeCommandName.TEST)) {
      return false;
    }
    return matchesTarget(config) && Objects.equals(testFilter, commonState.getTestFilterState().getTestFilter());
  }

  /** Returns true if the target is successfully set up. */
  abstract boolean setupTarget(BlazeCommandRunConfiguration config);

  /** Returns true if the run configuration target matches this {@link TestContext}. */
  abstract boolean matchesTarget(BlazeCommandRunConfiguration config);

  static class KnownTargetTestContext extends TestContext {

    final TargetInfo target;

    KnownTargetTestContext(
        TargetInfo target,
        PsiElement sourceElement,
        @Nullable String testFilter,
        @Nullable String description
    ) {
      super(sourceElement, testFilter, description);
      this.target = target;
    }

    @Override
    boolean setupTarget(BlazeCommandRunConfiguration config) {
      config.setTargetInfo(target);
      return true;
    }

    @Override
    boolean matchesTarget(BlazeCommandRunConfiguration config) {
      return target.label.equals(config.getSingleTarget());
    }
  }

  public static Builder builder(
      PsiElement sourceElement, ImmutableSet<ExecutorType> supportedExecutors) {
    return new Builder(sourceElement, supportedExecutors);
  }

  /** Builder class for {@link TestContext}. */
  public static class Builder {

    private final PsiElement sourceElement;
    private final ImmutableSet<ExecutorType> supportedExecutors;
    private ListenableFuture<RunConfigurationContext> contextFuture = null;
    private ListenableFuture<TargetInfo> targetFuture = null;

    @Nullable
    private String testFilter = null;

    @Nullable
    private String description = null;

    private Builder(PsiElement sourceElement, ImmutableSet<ExecutorType> supportedExecutors) {
      this.sourceElement = sourceElement;
      this.supportedExecutors = supportedExecutors;
    }

    @CanIgnoreReturnValue
    public Builder setContextFuture(ListenableFuture<RunConfigurationContext> contextFuture) {
      this.contextFuture = contextFuture;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder setTarget(ListenableFuture<TargetInfo> future) {
      this.targetFuture = future;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder setTarget(TargetInfo target) {
      this.targetFuture = Futures.immediateFuture(target);
      return this;
    }

    @CanIgnoreReturnValue
    public Builder setTestFilter(@Nullable String filter) {
      this.testFilter = filter;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder setDescription(String description) {
      this.description = description;
      return this;
    }

    public TestContext build() {
      if (contextFuture != null) {
        Preconditions.checkState(targetFuture == null);
        return new PendingAsyncTestContext(
            supportedExecutors,
            contextFuture,
            "Resolving test context",
            sourceElement,
            testFilter,
            description);
      }
      Preconditions.checkState(targetFuture != null);
      return PendingAsyncTestContext.fromTargetFuture(
          supportedExecutors, targetFuture, sourceElement, testFilter, description);
    }
  }
}
