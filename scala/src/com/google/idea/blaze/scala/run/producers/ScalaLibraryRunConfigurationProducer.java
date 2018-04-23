package com.google.idea.blaze.scala.run.producers;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.idea.blaze.base.command.BlazeFlags;
import com.google.idea.blaze.base.ideinfo.JavaIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetKey;
import com.google.idea.blaze.base.ideinfo.TargetMap;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration;
import com.google.idea.blaze.base.run.BlazeConfigurationNameBuilder;
import com.google.idea.blaze.base.run.state.BlazeCommandRunConfigurationCommonState;
import com.google.idea.blaze.base.run.testmap.FilteredTargetMap;
import com.intellij.execution.BeforeRunTask;
import com.intellij.execution.RunManagerEx;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject;

import java.util.List;

public class ScalaLibraryRunConfigurationProducer
  extends BlazeScalaMainClassRunConfigurationProducer {
  public static final String SCALA_BINARY_FOR_LIBS_MAP_KEY = "BlazeScalaBinaryForLibsMap";

  protected ScalaLibraryRunConfigurationProducer() {
    super(SCALA_BINARY_FOR_LIBS_MAP_KEY);
  }

  @Override
  protected FilteredTargetMap computeTargetMap(Project project, BlazeProjectData projectData) {
    return createBinaryTargetsMap(project, projectData);
  }

  @NotNull
  public static FilteredTargetMap createBinaryTargetsMap(Project project, BlazeProjectData projectData) {
    return new FilteredTargetMap(
      project,
      projectData.artifactLocationDecoder,
      toBinaryTargetMap(projectData.targetMap),
      (target) -> target.kind == Kind.SCALA_BINARY && target.isPlainTarget());
  }

  @Override
  protected boolean doSetupConfigFromContext(
    BlazeCommandRunConfiguration configuration,
    ConfigurationContext context,
    Ref<PsiElement> sourceElement) {
    boolean result = super.doSetupConfigFromContext(configuration, context, sourceElement);

    BlazeCommandRunConfigurationCommonState handlerState =
      configuration.getHandlerStateIfType(BlazeCommandRunConfigurationCommonState.class);
    if (handlerState == null) {
      return false;
    }

    handlerState.getBlazeFlagsState().setRawFlags(
      ImmutableList.of(
        BlazeFlags.NOCHECK_VISIBILITY,
        BlazeFlags.DELETED_PACKAGES));

    ScObject mainObject = getMainObject(context);
    if (mainObject == null) {
      return false;
    }
    setTargetMainClass(context, mainObject);

    String name =
      new BlazeConfigurationNameBuilder(configuration)
        .setTargetString(mainObject.name())
        .build();
    configuration.setName(name);
    configuration.setNameChangedByUser(true); // don't revert to generated name

    setTempBinaryTargetGeneratorTask(configuration);

    return result;
  }

  private void setTargetMainClass(ConfigurationContext context, ScObject mainObject) {
    TargetIdeInfo target = getTarget(context.getProject(), mainObject);
    if (target == null || target.javaIdeInfo == null) {
      return;
    }

    target.javaIdeInfo.javaBinaryMainClass = mainObject.getTruncedQualifiedName();
  }

  private void setTempBinaryTargetGeneratorTask(BlazeCommandRunConfiguration config) {
    Project project = config.getProject();
    RunManagerEx runManager = RunManagerEx.getInstanceEx(project);
    List<BeforeRunTask> beforeRunTasks = runManager.getBeforeRunTasks(config);
    beforeRunTasks.add(new ScalaGeneratedBinaryTargetRunTaskProvider(project).createTask(config));
    runManager.setBeforeRunTasks(config, beforeRunTasks);
  }

  private static TargetMap toBinaryTargetMap(TargetMap targetMap) {
    ImmutableMap<TargetKey, TargetIdeInfo> binaryTargets =
      targetMap.targets()
        .stream()
        .filter(t -> t.kind == Kind.SCALA_LIBRARY)
        .collect(ImmutableMap.toImmutableMap(
          k -> TargetKey.forPlainTarget(createLabel(k.key)),
          v -> toBinaryTarget(v)
        ));

    return new TargetMap(binaryTargets);
  }

  private static TargetIdeInfo toBinaryTarget(TargetIdeInfo targetIdeInfo) {
    TargetIdeInfo.Builder builder = TargetIdeInfo.builder()
      .setLabel(createLabel(targetIdeInfo.key))
      .setKind(Kind.SCALA_BINARY)
      .addRuntimeDep(targetIdeInfo.key.label)
      .setJavaInfo(new JavaIdeInfo.Builder());

    targetIdeInfo.sources.forEach(builder::addSource);

    return builder.build();
  }

  private static Label createLabel(TargetKey key) {
    return Label.create(String.format("//ijwb_tmp:%s-main",
      key.label.targetName()
    ));
  }
}
