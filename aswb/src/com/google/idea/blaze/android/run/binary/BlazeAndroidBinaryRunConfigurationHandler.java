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
package com.google.idea.blaze.android.run.binary;

import com.android.annotations.VisibleForTesting;
import com.android.tools.idea.run.ValidationError;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.idea.blaze.android.run.BlazeAndroidRunConfigurationCommonState;
import com.google.idea.blaze.android.run.BlazeAndroidRunConfigurationHandler;
import com.google.idea.blaze.android.run.BlazeAndroidRunConfigurationValidationUtil;
import com.google.idea.blaze.android.run.binary.AndroidBinaryLaunchMethodsUtils.AndroidBinaryLaunchMethod;
import com.google.idea.blaze.android.run.binary.mobileinstall.BlazeAndroidBinaryMobileInstallRunContext;
import com.google.idea.blaze.android.run.runner.BlazeAndroidRunConfigurationRunner;
import com.google.idea.blaze.android.run.runner.BlazeAndroidRunContext;
import com.google.idea.blaze.android.sync.projectstructure.BlazeAndroidProjectStructureSyncer;
import com.google.idea.blaze.base.command.BlazeCommandName;
import com.google.idea.blaze.base.command.BlazeInvocationContext;
import com.google.idea.blaze.base.logging.EventLoggingService;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.TargetExpression;
import com.google.idea.blaze.base.projectview.ProjectViewManager;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration;
import com.google.idea.blaze.base.run.BlazeCommandRunConfigurationType;
import com.google.idea.blaze.base.run.BlazeConfigurationNameBuilder;
import com.google.idea.blaze.base.run.ExecutorType;
import com.google.idea.blaze.base.run.confighandler.BlazeCommandRunConfigurationRunner;
import com.google.idea.blaze.base.run.state.RunConfigurationState;
import com.google.idea.blaze.base.settings.Blaze;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.RunManager;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.configurations.RuntimeConfigurationException;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.Nullable;

/**
 * {@link com.google.idea.blaze.base.run.confighandler.BlazeCommandRunConfigurationHandler} for
 * android_binary targets.
 */
public class BlazeAndroidBinaryRunConfigurationHandler
    implements BlazeAndroidRunConfigurationHandler {
  private static final Logger LOG =
      Logger.getInstance(BlazeAndroidBinaryRunConfigurationHandler.class);

  private final BlazeCommandRunConfiguration configuration;
  private final BlazeAndroidBinaryRunConfigurationState configState;

  // Keys to store state for the MI migration prompt
  private static final String MI_LAST_PROMPT = "MI_MIGRATE_LAST_PROMPT";
  static final String MI_NEVER_ASK_AGAIN = "MI_MIGRATE_NEVER_AGAIN";
  private static final Long MI_TIMEOUT_MS = TimeUnit.HOURS.toMillis(20); // 20 hours

  @VisibleForTesting
  protected BlazeAndroidBinaryRunConfigurationHandler(BlazeCommandRunConfiguration configuration) {
    this.configuration = configuration;
    configState =
        new BlazeAndroidBinaryRunConfigurationState(
            Blaze.buildSystemName(configuration.getProject()));
  }

  @Override
  public BlazeAndroidBinaryRunConfigurationState getState() {
    return configState;
  }

  @Override
  public BlazeAndroidRunConfigurationCommonState getCommonState() {
    return configState.getCommonState();
  }

  @Override
  @Nullable
  public Label getLabel() {
    TargetExpression target = configuration.getTarget();
    if (target instanceof Label) {
      return (Label) target;
    }
    return null;
  }

  @Nullable
  public Module getModule() {
    Label target = getLabel();
    return target != null
        ? BlazeAndroidProjectStructureSyncer.ensureRunConfigurationModule(
            configuration.getProject(), target)
        : null;
  }

  @Override
  public BlazeCommandRunConfigurationRunner createRunner(
      Executor executor, ExecutionEnvironment env) throws ExecutionException {
    Project project = env.getProject();

    // This is a workaround for b/134587683
    // Due to the way blaze run configuration editors update the underlying configuration state,
    // it's possible for the configuration referenced in this handler to be out of date. This can
    // cause tricky side-effects such as incorrect build target and target validation settings.
    // Fortunately, the only field that can come out of sync is the target label and it's target
    // kind. The handlers are designed to only handle their supported target kinds, so we can
    // safely ignore all fields other than target label itself and extract an up to date target
    // label from the execution environment.
    // Validation of the updated target label is not needed here because:
    // 1. The target kind is guaranteed to be an android instrumentation test kind or else this
    //    specific handler will not be used.
    // 2. Any other validation is done during edit-time of the run configuration before saving.
    BlazeCommandRunConfiguration configFromEnv =
        BlazeAndroidRunConfigurationHandler.getCommandConfig(env);
    configuration.setTarget(configFromEnv.getTarget());

    Module module = getModule();
    AndroidFacet facet = module != null ? AndroidFacet.getInstance(module) : null;
    ProjectViewSet projectViewSet = ProjectViewManager.getInstance(project).getProjectViewSet();
    BlazeAndroidRunConfigurationValidationUtil.validateExecution(module, facet, projectViewSet);

    ImmutableList<String> blazeFlags =
        configState
            .getCommonState()
            .getExpandedBuildFlags(
                project,
                projectViewSet,
                BlazeCommandName.RUN,
                BlazeInvocationContext.runConfigContext(
                    ExecutorType.fromExecutor(env.getExecutor()), configuration.getType(), false));
    ImmutableList<String> exeFlags =
        ImmutableList.copyOf(
            configState.getCommonState().getExeFlagsState().getFlagsForExternalProcesses());
    BlazeAndroidRunContext runContext = createRunContext(project, facet, env, blazeFlags, exeFlags);

    return new BlazeAndroidRunConfigurationRunner(
        module,
        runContext,
        getCommonState().getDeployTargetManager(),
        getCommonState().getDebuggerManager(),
        configuration);
  }

  private BlazeAndroidRunContext createRunContext(
      Project project,
      AndroidFacet facet,
      ExecutionEnvironment env,
      ImmutableList<String> blazeFlags,
      ImmutableList<String> exeFlags) {
    switch (configState.getLaunchMethod()) {
      case NON_BLAZE:
        if (!maybeShowMobileInstallOptIn(project, configuration)) {
          return new BlazeAndroidBinaryNormalBuildRunContext(
              project, facet, configuration, env, configState, getLabel(), blazeFlags);
        }
        // fall through
      case MOBILE_INSTALL_V2:
        // Standardize on a single mobile-install launch method
        configState.setLaunchMethod(AndroidBinaryLaunchMethod.MOBILE_INSTALL);
        // fall through
      case MOBILE_INSTALL:
        return new BlazeAndroidBinaryMobileInstallRunContext(
            project, facet, configuration, env, configState, getLabel(), blazeFlags, exeFlags);
    }
    throw new AssertionError();
  }

  @Override
  public final void checkConfiguration() throws RuntimeConfigurationException {
    BlazeAndroidRunConfigurationValidationUtil.throwTopConfigurationError(validate());
  }

  /**
   * We collect errors rather than throwing to avoid missing fatal errors by exiting early for a
   * warning. We use a separate method for the collection so the compiler prevents us from
   * accidentally throwing.
   */
  private List<ValidationError> validate() {
    List<ValidationError> errors = Lists.newArrayList();
    Module module = getModule();
    errors.addAll(BlazeAndroidRunConfigurationValidationUtil.validateModule(module));
    AndroidFacet facet = null;
    if (module != null) {
      facet = AndroidFacet.getInstance(module);
      errors.addAll(BlazeAndroidRunConfigurationValidationUtil.validateFacet(facet, module));
    }
    errors.addAll(configState.validate(facet));
    return errors;
  }

  @Override
  @Nullable
  public String suggestedName(BlazeCommandRunConfiguration configuration) {
    Label target = getLabel();
    if (target == null) {
      return null;
    }
    // buildSystemName and commandName are intentionally omitted.
    return new BlazeConfigurationNameBuilder().setTargetString(target).build();
  }

  @Override
  @Nullable
  public BlazeCommandName getCommandName() {
    return BlazeCommandName.RUN;
  }

  @Override
  public String getHandlerName() {
    return "Android Binary Handler";
  }

  /**
   * Maybe shows the mobile-install optin dialog, and migrates project as appropriate.
   *
   * <p>Will only be shown once per project in a 20 hour window, with the ability to permanently
   * dismiss for this project.
   *
   * <p>If the user selects "Yes", all BlazeAndroidBinaryRunConfigurations in this project will be
   * migrated to use mobile-install.
   *
   * @return true if dialog was shown and user migrated, otherwise false
   */
  private boolean maybeShowMobileInstallOptIn(
      Project project, BlazeCommandRunConfiguration configuration) {
    long lastPrompt = PropertiesComponent.getInstance(project).getOrInitLong(MI_LAST_PROMPT, 0L);
    boolean neverAsk =
        PropertiesComponent.getInstance(project).getBoolean(MI_NEVER_ASK_AGAIN, false);
    if (neverAsk || (System.currentTimeMillis() - lastPrompt) < MI_TIMEOUT_MS) {
      return false;
    }
    // Add more logging on why the MI opt-in dialog is shown.  There exists a bug there a user
    // is shown the mobile-install opt-in dialog every time they switch clients. The only way for
    // this to happen is if a new target is created or if the timeouts are not behaving as expected.
    // TODO Remove once b/130327673 is resolved.
    LOG.info(
        "Showing mobile install opt-in dialog.\n"
            + "Run target: "
            + configuration.getTarget()
            + "\n"
            + "Time since last prompt: "
            + (System.currentTimeMillis() - lastPrompt));
    PropertiesComponent.getInstance(project)
        .setValue(MI_LAST_PROMPT, String.valueOf(System.currentTimeMillis()));
    int choice =
        Messages.showYesNoCancelDialog(
            project,
            "Blaze mobile-install (go/blaze-mi) introduces fast, incremental builds and deploys "
                + "for Android development.\nBlaze mobile-install is the default for new Android "
                + "Studio projects, but you're still using Blaze build.\n\nSwitch all run "
                + "configurations in this project to use Blaze mobile-install?",
            "Switch to Blaze mobile-install?",
            "Yes",
            "Not now",
            "Never ask again for this project",
            Messages.getQuestionIcon());
    if (choice == Messages.YES) {
      Messages.showInfoMessage(
          String.format(
              "Successfully migrated %d run configuration(s) to mobile-install",
              doMigrate(project)),
          "Success!");
    } else if (choice == Messages.NO) {
      // Do nothing, dialog will not be shown until the wait period has elapsed
    } else if (choice == Messages.CANCEL) {
      PropertiesComponent.getInstance(project).setValue(MI_NEVER_ASK_AGAIN, true);
    }
    EventLoggingService.getInstance()
        .logEvent(
            getClass(), "mi_migrate_prompt", ImmutableMap.of("choice", choiceToString(choice)));
    return choice == Messages.YES;
  }

  private int doMigrate(Project project) {
    int count = 0;
    for (RunConfiguration runConfig :
        RunManager.getInstance(project)
            .getConfigurationsList(BlazeCommandRunConfigurationType.getInstance())) {
      if (runConfig instanceof BlazeCommandRunConfiguration) {
        RunConfigurationState state =
            ((BlazeCommandRunConfiguration) runConfig).getHandler().getState();
        if (state instanceof BlazeAndroidBinaryRunConfigurationState) {
          ((BlazeAndroidBinaryRunConfigurationState) state)
              .setLaunchMethod(AndroidBinaryLaunchMethod.MOBILE_INSTALL);
          count++;
        }
      }
    }
    return count;
  }

  private String choiceToString(int choice) {
    if (choice == Messages.YES) {
      return "yes";
    } else if (choice == Messages.NO) {
      return "not_now";
    } else if (choice == Messages.CANCEL) {
      return "never_for_project";
    } else {
      return "unknown";
    }
  }
}
