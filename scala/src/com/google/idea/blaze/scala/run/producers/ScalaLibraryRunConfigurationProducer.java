package com.google.idea.blaze.scala.run.producers;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.idea.blaze.base.command.BlazeFlags;
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
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;

import java.util.List;

public class ScalaLibraryRunConfigurationProducer
  extends BlazeScalaMainClassRunConfigurationProducer {
  private static final String SCALA_BINARY_FOR_LIBS_MAP_KEY = "BlazeScalaBinaryForLibsMap";

  protected ScalaLibraryRunConfigurationProducer() {
    super(SCALA_BINARY_FOR_LIBS_MAP_KEY);
  }

  @Override
  protected FilteredTargetMap computeTargetMap(Project project, BlazeProjectData projectData) {
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
      ImmutableList.of(BlazeFlags.NOCHECK_VISIBILITY));

    PsiMethod targetMethod = (PsiMethod)sourceElement.get();
    if (targetMethod == null)
      return false;

    String targetName = targetMethod.getContainingClass().getName() + "." + targetMethod.getName();

    String name =
      new BlazeConfigurationNameBuilder(configuration)
        .setTargetString(targetName)
        .build();
    configuration.setName(name);
    configuration.setNameChangedByUser(true); // don't revert to generated name

    setTempBinaryTargetGeneratorTask(configuration);

    return result;
  }

  private void setTempBinaryTargetGeneratorTask(BlazeCommandRunConfiguration config) {
    RunManagerEx runManager = RunManagerEx.getInstanceEx(config.getProject());
    List<BeforeRunTask> beforeRunTasks = runManager.getBeforeRunTasks(config);
    beforeRunTasks.add(new ScalaGeneratedBinaryTargetRunTaskProvider.Task());
    runManager.setBeforeRunTasks(config, beforeRunTasks);
  }

  private TargetMap toBinaryTargetMap(TargetMap targetMap) {
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

  private TargetIdeInfo toBinaryTarget(TargetIdeInfo targetIdeInfo) {
    System.out.println(targetIdeInfo);
    TargetIdeInfo.Builder builder = TargetIdeInfo.builder()
      .setLabel(createLabel(targetIdeInfo.key))
      .setKind(Kind.SCALA_BINARY);

    targetIdeInfo.sources.forEach(builder::addSource);

    return builder.build();
  }

  private static Label createLabel(TargetKey key) {
    return Label.create(String.format("//ijwb_tmp/%d-%s:main",
      key.label.hashCode(),
      key.label.targetName()
    ));
  }
}
