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
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.idea.blaze.base.command.BlazeCommandName;
import com.google.idea.blaze.base.command.BlazeFlags;
import com.google.idea.blaze.base.dependencies.TargetInfo;
import com.google.idea.blaze.base.execution.BlazeParametersListUtil;
import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration;
import com.google.idea.blaze.base.run.BlazeConfigurationNameBuilder;
import com.google.idea.blaze.base.run.ExecutorType;
import com.google.idea.blaze.base.run.PendingRunConfigurationContext;
import com.google.idea.blaze.base.run.state.BlazeCommandRunConfigurationCommonState;
import com.google.idea.blaze.base.run.state.RunConfigurationFlagsState;
import com.google.idea.blaze.base.settings.Blaze;
import com.intellij.execution.RunCanceledByUserException;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.PerformInBackgroundOption;
import com.intellij.openapi.progress.impl.BackgroundableProcessIndicator;
import com.intellij.openapi.progress.util.AbstractProgressIndicatorExBase;
import com.intellij.openapi.progress.util.ProgressWindow;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.util.ui.UIUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import javax.annotation.Nullable;

/** A context related to a blaze test target, used to configure a run configuration. */
public abstract class TestContext implements RunConfigurationContext {

  final PsiElement sourceElement;
  final ImmutableList<BlazeFlagsModification> blazeFlags;
  @Nullable final String description;

  TestContext(
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
    if (!setupTarget(config)) {
      return false;
    }
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
  public boolean matchesRunConfiguration(BlazeCommandRunConfiguration config) {
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

  /** Returns true if the target is successfully set up. */
  abstract boolean setupTarget(BlazeCommandRunConfiguration config);

  /** Returns true if the run configuration target matches this {@link TestContext}. */
  abstract boolean matchesTarget(BlazeCommandRunConfiguration config);

  static class KnownTargetTestContext extends TestContext {
    final TargetInfo target;

    KnownTargetTestContext(
        TargetInfo target,
        PsiElement sourceElement,
        ImmutableList<BlazeFlagsModification> blazeFlags,
        @Nullable String description) {
      super(sourceElement, blazeFlags, description);
      this.target = target;
    }

    @Override
    boolean setupTarget(BlazeCommandRunConfiguration config) {
      config.setTargetInfo(target);
      return true;
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
  private static class PendingAsyncTestContext extends TestContext
      implements PendingRunConfigurationContext {
    private static final Logger logger = Logger.getInstance(PendingAsyncTestContext.class);

    private static PendingAsyncTestContext fromTargetFuture(
        ImmutableSet<ExecutorType> supportedExecutors,
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
                  return new FailedPendingRunConfiguration(
                      sourceElement, String.format("No %s target found.", buildSystem));
                }
                RunConfigurationContext context =
                    PendingWebTestContext.findWebTestContext(
                        supportedExecutors, t, sourceElement, blazeFlags, description);
                return context != null
                    ? context
                    : new KnownTargetTestContext(t, sourceElement, blazeFlags, description);
              },
              MoreExecutors.directExecutor());
      return new PendingAsyncTestContext(
          supportedExecutors, future, progressMessage, sourceElement, blazeFlags, description);
    }

    private final ImmutableSet<ExecutorType> supportedExecutors;
    private final ListenableFuture<RunConfigurationContext> future;
    private final String progressMessage;

    private PendingAsyncTestContext(
        ImmutableSet<ExecutorType> supportedExecutors,
        ListenableFuture<RunConfigurationContext> future,
        String progressMessage,
        PsiElement sourceElement,
        ImmutableList<BlazeFlagsModification> blazeFlags,
        @Nullable String description) {
      super(sourceElement, blazeFlags, description);
      this.supportedExecutors = supportedExecutors;
      this.future = recursivelyResolveContext(future);
      this.progressMessage = progressMessage;
    }

    @Override
    public ImmutableSet<ExecutorType> supportedExecutors() {
      return supportedExecutors;
    }

    @Override
    public boolean isDone() {
      return future.isDone();
    }

    @Override
    public void resolve(
        ExecutionEnvironment env, BlazeCommandRunConfiguration config, Runnable rerun)
        throws com.intellij.execution.ExecutionException {
      waitForFutureUnderProgressDialog(env.getProject());
      rerun.run();
    }

    @Override
    boolean setupTarget(BlazeCommandRunConfiguration config) {
      config.setPendingContext(this);
      if (future.isDone()) {
        // set it up synchronously, and return the result
        return doSetupPendingContext(config);
      } else {
        future.addListener(() -> doSetupPendingContext(config), MoreExecutors.directExecutor());
        return true;
      }
    }

    private boolean doSetupPendingContext(BlazeCommandRunConfiguration config) {
      try {
        RunConfigurationContext context = getFutureHandlingErrors();
        boolean success = context.setupRunConfiguration(config);
        if (success) {
          if (config.getPendingContext() == this) {
            // remove this pending context from the config since it is done
            // however, if context became the new pending context, leave it alone
            config.clearPendingContext();
          }
          return true;
        }
      } catch (RunCanceledByUserException | NoRunConfigurationFoundException e) {
        // silently ignore
      } catch (com.intellij.execution.ExecutionException e) {
        logger.warn(e);
      }
      return false;
    }

    @Override
    public boolean matchesRunConfiguration(BlazeCommandRunConfiguration config) {
      if (!future.isDone()) {
        return super.matchesRunConfiguration(config);
      }
      try {
        RunConfigurationContext context = future.get();
        return context.matchesRunConfiguration(config);
      } catch (ExecutionException | InterruptedException e) {
        return false;
      }
    }

    @Override
    boolean matchesTarget(BlazeCommandRunConfiguration config) {
      return getSourceElementString().equals(config.getContextElementString());
    }

    /**
     * Returns a future with all currently-unknown details of this configuration context resolved.
     *
     * <p>Handles the case where there are nested {@link PendingAsyncTestContext}s.
     */
    private static ListenableFuture<RunConfigurationContext> recursivelyResolveContext(
        ListenableFuture<RunConfigurationContext> future) {
      return Futures.transformAsync(
          future,
          c ->
              c instanceof PendingAsyncTestContext
                  ? recursivelyResolveContext(((PendingAsyncTestContext) c).future)
                  : Futures.immediateFuture(c),
          MoreExecutors.directExecutor());
    }

    /**
     * Waits for the run configuration to be configured, displaying a progress dialog if necessary.
     *
     * @throws com.intellij.execution.ExecutionException if the run configuration is not
     *     successfully configured
     */
    private void waitForFutureUnderProgressDialog(Project project)
        throws com.intellij.execution.ExecutionException {
      if (future.isDone()) {
        getFutureHandlingErrors();
      }
      // The progress indicator must be created on the UI thread.
      ProgressWindow indicator =
          UIUtil.invokeAndWaitIfNeeded(
              () ->
                  new BackgroundableProcessIndicator(
                      project,
                      progressMessage,
                      PerformInBackgroundOption.ALWAYS_BACKGROUND,
                      "Cancel",
                      "Cancel",
                      /* cancellable= */ true));

      indicator.setIndeterminate(true);
      indicator.start();
      indicator.addStateDelegate(
          new AbstractProgressIndicatorExBase() {
            @Override
            public void cancel() {
              super.cancel();
              future.cancel(true);
            }
          });
      try {
        getFutureHandlingErrors();
      } finally {
        if (indicator.isRunning()) {
          indicator.stop();
          indicator.processFinish();
        }
      }
    }

    private RunConfigurationContext getFutureHandlingErrors()
        throws com.intellij.execution.ExecutionException {
      try {
        RunConfigurationContext result = future.get();
        if (result == null) {
          throw new NoRunConfigurationFoundException("Run configuration setup failed.");
        }
        if (result instanceof FailedPendingRunConfiguration) {
          throw new NoRunConfigurationFoundException(
              ((FailedPendingRunConfiguration) result).errorMessage);
        }
        return result;
      } catch (InterruptedException e) {
        throw new RunCanceledByUserException();
      } catch (ExecutionException e) {
        throw new com.intellij.execution.ExecutionException(e);
      }
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
            flags.add(BlazeFlags.TEST_FILTER + "=" + BlazeParametersListUtil.encodeParam(filter));
          }
        }

        @Override
        public boolean matchesConfigState(RunConfigurationFlagsState state) {
          return state
              .getRawFlags()
              .contains(BlazeFlags.TEST_FILTER + "=" + BlazeParametersListUtil.encodeParam(filter));
        }
      };
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
    private final ImmutableList.Builder<BlazeFlagsModification> blazeFlags =
        ImmutableList.builder();
    private String description = null;

    private Builder(PsiElement sourceElement, ImmutableSet<ExecutorType> supportedExecutors) {
      this.sourceElement = sourceElement;
      this.supportedExecutors = supportedExecutors;
    }

    public Builder setContextFuture(ListenableFuture<RunConfigurationContext> contextFuture) {
      this.contextFuture = contextFuture;
      return this;
    }

    public Builder setTarget(ListenableFuture<TargetInfo> future) {
      this.targetFuture = future;
      return this;
    }

    public Builder setTarget(TargetInfo target) {
      this.targetFuture = Futures.immediateFuture(target);
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
      if (contextFuture != null) {
        Preconditions.checkState(targetFuture == null);
        return new PendingAsyncTestContext(
            supportedExecutors,
            contextFuture,
            "Resolving test context",
            sourceElement,
            blazeFlags.build(),
            description);
      }
      Preconditions.checkState(targetFuture != null);
      return PendingAsyncTestContext.fromTargetFuture(
          supportedExecutors, targetFuture, sourceElement, blazeFlags.build(), description);
    }
  }
}
