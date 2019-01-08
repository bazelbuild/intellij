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
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.idea.blaze.base.command.BlazeCommandName;
import com.google.idea.blaze.base.command.BlazeFlags;
import com.google.idea.blaze.base.dependencies.TargetInfo;
import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration;
import com.google.idea.blaze.base.run.BlazeConfigurationNameBuilder;
import com.google.idea.blaze.base.run.PendingRunConfigurationContext;
import com.google.idea.blaze.base.run.state.BlazeCommandRunConfigurationCommonState;
import com.google.idea.blaze.base.run.state.RunConfigurationFlagsState;
import com.google.idea.blaze.base.run.targetfinder.FuturesUtil;
import com.google.idea.blaze.base.settings.Blaze;
import com.intellij.psi.PsiElement;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nullable;

/** A context related to a blaze test target, used to configure a run configuration. */
public abstract class TestContext implements RunConfigurationContext {

  final PsiElement sourceElement;
  final ImmutableList<BlazeFlagsModification> blazeFlags;
  @Nullable final String description;

  private TestContext(
      PsiElement sourceElement,
      ImmutableList<BlazeFlagsModification> blazeFlags,
      @Nullable String description) {
    this.sourceElement = sourceElement;
    this.blazeFlags = blazeFlags;
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
    setupTarget(config);
    BlazeCommandRunConfigurationCommonState commonState =
        config.getHandlerStateIfType(BlazeCommandRunConfigurationCommonState.class);
    if (commonState == null) {
      return false;
    }
    commonState.getCommandState().setCommand(BlazeCommandName.TEST);

    List<String> flags = new ArrayList<>(commonState.getBlazeFlagsState().getRawFlags());
    blazeFlags.forEach(m -> m.modifyFlags(flags));
    commonState.getBlazeFlagsState().setRawFlags(flags);

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
  public final boolean matchesRunConfiguration(BlazeCommandRunConfiguration config) {
    BlazeCommandRunConfigurationCommonState commonState =
        config.getHandlerStateIfType(BlazeCommandRunConfigurationCommonState.class);
    if (commonState == null) {
      return false;
    }
    if (!Objects.equals(commonState.getCommandState().getCommand(), BlazeCommandName.TEST)) {
      return false;
    }
    RunConfigurationFlagsState flagsState = commonState.getBlazeFlagsState();
    return matchesTarget(config)
        && blazeFlags.stream().allMatch(m -> m.matchesConfigState(flagsState));
  }

  abstract void setupTarget(BlazeCommandRunConfiguration config);

  /** Returns true if the run configuration target matches this {@link TestContext}. */
  abstract boolean matchesTarget(BlazeCommandRunConfiguration config);

  private static class KnownTargetTestContext extends TestContext {
    final TargetInfo target;

    private KnownTargetTestContext(
        TargetInfo target,
        PsiElement sourceElement,
        ImmutableList<BlazeFlagsModification> blazeFlags,
        @Nullable String description) {
      super(sourceElement, blazeFlags, description);
      this.target = target;
    }

    @Override
    void setupTarget(BlazeCommandRunConfiguration config) {
      config.setTargetInfo(target);
    }

    @Override
    boolean matchesTarget(BlazeCommandRunConfiguration config) {
      return target.label.equals(config.getTarget());
    }
  }

  /**
   * For situations where we appear to be in a recognized test context, but can't efficiently
   * resolve the psi elements and/or relevant blaze target.
   *
   * <p>A {@link BlazeCommandRunConfiguration} will be produced synchronously, then filled in later
   * when the full context is known.
   */
  private static class PendingContextTestContext extends TestContext
      implements PendingRunConfigurationContext {

    private static PendingContextTestContext fromTargetFuture(
        ListenableFuture<TargetInfo> target,
        PsiElement sourceElement,
        ImmutableList<BlazeFlagsModification> blazeFlags,
        @Nullable String description) {
      String buildSystem = Blaze.buildSystemName(sourceElement.getProject());
      String progressMessage = String.format("Searching for %s target", buildSystem);
      ListenableFuture<RunConfigurationContext> future =
          Futures.transform(
              target,
              t -> {
                if (t == null) {
                  throw new RuntimeException(String.format("No %s target found.", buildSystem));
                }
                return new KnownTargetTestContext(t, sourceElement, blazeFlags, description);
              },
              MoreExecutors.directExecutor());
      return new PendingContextTestContext(
          future, progressMessage, sourceElement, blazeFlags, description);
    }

    private final ListenableFuture<RunConfigurationContext> future;
    private final String progressMessage;

    private PendingContextTestContext(
        ListenableFuture<RunConfigurationContext> future,
        String progressMessage,
        PsiElement sourceElement,
        ImmutableList<BlazeFlagsModification> blazeFlags,
        @Nullable String description) {
      super(sourceElement, blazeFlags, description);
      this.future = PendingRunConfigurationContext.recursivelyResolveContext(future);
      this.progressMessage = progressMessage;
    }

    @Override
    public ListenableFuture<RunConfigurationContext> getFuture() {
      return future;
    }

    @Override
    public String getProgressMessage() {
      return progressMessage;
    }

    @Override
    void setupTarget(BlazeCommandRunConfiguration config) {
      config.setPendingContext(this);
    }

    @Override
    boolean matchesTarget(BlazeCommandRunConfiguration config) {
      return sourceElement.toString().equals(config.getContextElementString());
    }
  }

  /**
   * A modification to the blaze flags list for a run configuration. For example, setting a test
   * filter.
   */
  public interface BlazeFlagsModification {
    void modifyFlags(List<String> flags);

    boolean matchesConfigState(RunConfigurationFlagsState state);

    static BlazeFlagsModification addFlagIfNotPresent(String flag) {
      return new BlazeFlagsModification() {
        @Override
        public void modifyFlags(List<String> flags) {
          if (!flags.contains(flag)) {
            flags.add(flag);
          }
        }

        @Override
        public boolean matchesConfigState(RunConfigurationFlagsState state) {
          return state.getRawFlags().contains(flag);
        }
      };
    }

    static BlazeFlagsModification testFilter(String filter) {
      return new BlazeFlagsModification() {
        @Override
        public void modifyFlags(List<String> flags) {
          // remove old test filter flag if present
          flags.removeIf((flag) -> flag.startsWith(BlazeFlags.TEST_FILTER));
          if (filter != null) {
            flags.add(BlazeFlags.TEST_FILTER + "=" + filter);
          }
        }

        @Override
        public boolean matchesConfigState(RunConfigurationFlagsState state) {
          return state.getRawFlags().contains(BlazeFlags.TEST_FILTER + "=" + filter);
        }
      };
    }
  }

  public static Builder builder() {
    return new Builder();
  }

  /** Builder class for {@link TestContext}. */
  public static class Builder {
    private ListenableFuture<RunConfigurationContext> contextFuture = null;
    private ListenableFuture<TargetInfo> targetFuture = null;
    private TargetInfo target = null;
    private PsiElement sourceElement = null;
    private final ImmutableList.Builder<BlazeFlagsModification> blazeFlags =
        ImmutableList.builder();
    private String description = null;

    public Builder setContextFuture(ListenableFuture<RunConfigurationContext> contextFuture) {
      this.contextFuture = contextFuture;
      return this;
    }

    public Builder setTarget(ListenableFuture<TargetInfo> future) {
      if (future.isDone()) {
        TargetInfo target = FuturesUtil.getIgnoringErrors(future);
        if (target != null) {
          this.target = target;
        } else {
          this.targetFuture = future;
        }
      } else {
        this.targetFuture = future;
      }
      return this;
    }

    public Builder setTarget(TargetInfo target) {
      this.target = target;
      return this;
    }

    public Builder setSourceElement(PsiElement sourceElement) {
      this.sourceElement = sourceElement;
      return this;
    }

    public Builder setTestFilter(@Nullable String filter) {
      if (filter != null) {
        blazeFlags.add(BlazeFlagsModification.testFilter(filter));
      }
      return this;
    }

    public Builder addBlazeFlagsModification(BlazeFlagsModification modification) {
      this.blazeFlags.add(modification);
      return this;
    }

    public Builder setDescription(String description) {
      this.description = description;
      return this;
    }

    public TestContext build() {
      Preconditions.checkNotNull(sourceElement);
      if (contextFuture != null) {
        Preconditions.checkState(targetFuture == null && target == null);
        return new PendingContextTestContext(
            contextFuture,
            "Resolving test context",
            sourceElement,
            blazeFlags.build(),
            description);
      }
      Preconditions.checkState(targetFuture == null ^ target == null);
      if (target != null) {
        return new KnownTargetTestContext(target, sourceElement, blazeFlags.build(), description);
      }
      return PendingContextTestContext.fromTargetFuture(
          targetFuture, sourceElement, blazeFlags.build(), description);
    }
  }
}
