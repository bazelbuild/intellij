package com.google.idea.blaze.scala.run.producers;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.run.producers.BlazeRunConfigurationProducer;
import com.google.idea.blaze.base.run.testmap.FilteredTargetMap;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.java.run.RunUtil;
import com.intellij.execution.RunManagerEx;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.application.ApplicationConfiguration;
import com.intellij.execution.application.ApplicationConfigurationType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiElement;
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject;

import java.io.File;
import java.util.Collection;

import static com.google.idea.blaze.scala.run.producers.BlazeScalaMainClassRunConfigurationProducer.getMainObject;

public class DeployableJarRunConfigurationProducer
  extends BlazeRunConfigurationProducer<ApplicationConfiguration> {

  public static final Key<Label> TARGET_LABEL =
    Key.create("blaze.scala.library.target.label");

  protected DeployableJarRunConfigurationProducer() {
    super(ApplicationConfigurationType.getInstance());
  }

  @Override
  protected boolean doSetupConfigFromContext(
    ApplicationConfiguration configuration,
    ConfigurationContext context,
    Ref<PsiElement> sourceElement) {

    ScObject mainObject = getMainObject(context);
    if (mainObject == null) {
      return false;
    }

    File mainObjectFile = RunUtil.getFileForClass(mainObject);
    if (mainObjectFile == null) {
      return false;
    }

    TargetIdeInfo target = findTarget(context.getProject(), mainObjectFile);
    if (target == null) {
      return false;
    }

    Label label = target.key.label;
    File jarFile = getDeployJarFile(label, context.getProject());

    configuration.setVMParameters("-cp " + jarFile.getPath());
    configuration.setMainClassName(mainObject.getTruncedQualifiedName());
    configuration.setModule(context.getModule());

    configuration.putUserData(TARGET_LABEL, label);
    configuration.setName(mainObject.name());
    configuration.setNameChangedByUser(true); // don't revert to generated name

    setDeployableJarGeneratorTask(configuration);

    return true;
  }

  @Override
  protected boolean doIsConfigFromContext(
    ApplicationConfiguration configuration,
    ConfigurationContext context) {
    ScObject mainObject = getMainObject(context);
    if (mainObject == null) {
      return false;
    }

    File mainObjectFile = RunUtil.getFileForClass(mainObject);
    if (mainObjectFile == null) {
      return false;
    }

    TargetIdeInfo target = findTarget(context.getProject(), mainObjectFile);
    if (target == null) {
      return false;
    }

    if (configuration.getMainClass() == null) {
      return false;
    }
    if (configuration.getVMParameters() == null) {
      return false;
    }

    Label label = target.key.label;
    File jarFile = getDeployJarFile(label, context.getProject());

    return configuration.getMainClass().getQualifiedName().equals(mainObject.getTruncedQualifiedName()) &&
           configuration.getVMParameters().contains("-cp " + jarFile.getPath());
  }

  private TargetIdeInfo findTarget(Project project, File sourceFile) {
    BlazeProjectData projectData =
      BlazeProjectDataManager.getInstance(project).getBlazeProjectData();
    if (projectData == null) {
      return null;
    }

    Collection<TargetIdeInfo> targets =
      new FilteredTargetMap(
        project,
        projectData.artifactLocationDecoder,
        projectData.targetMap,
        t -> t.kind == Kind.SCALA_LIBRARY && t.isPlainTarget())
        .targetsForSourceFile(sourceFile);

    return Iterables.getFirst(targets, null);
  }

  private void setDeployableJarGeneratorTask(ApplicationConfiguration config) {
    Project project = config.getProject();
    RunManagerEx runManager = RunManagerEx.getInstanceEx(project);
    runManager.setBeforeRunTasks(config,
      ImmutableList.of(new GenerateExecutableDeployableJarProviderTaskProvider.Task()));
  }

  public File getDeployJarFile(Label target, Project project) {
    WorkspaceRoot root = WorkspaceRoot.fromProject(project);
    return root.fileForPath(WorkspacePath.createIfValid(
      String.format("bazel-bin/%s_deploy.jar", target.targetName())));
  }
}
