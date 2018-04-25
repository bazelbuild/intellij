package com.google.idea.blaze.scala.run.producers;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.idea.blaze.base.buildmodifier.BuildFileModifier;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.lang.buildfile.actions.BuildFileModifierImpl;
import com.google.idea.blaze.base.lang.buildfile.psi.BuildFile;
import com.google.idea.blaze.base.model.primitives.*;
import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration;
import com.google.idea.blaze.base.run.testmap.FilteredTargetMap;
import com.google.idea.blaze.base.sync.SyncCache;
import com.intellij.execution.BeforeRunTask;
import com.intellij.execution.BeforeRunTaskProvider;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import icons.BlazeIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.stream.Collectors;

import static com.google.idea.blaze.scala.run.producers.ScalaLibraryRunConfigurationProducer.SCALA_BINARY_FOR_LIBS_MAP_KEY;

public class ScalaGeneratedBinaryTargetRunTaskProvider
  extends BeforeRunTaskProvider<ScalaGeneratedBinaryTargetRunTaskProvider.Task> {
  public static final Key<ScalaGeneratedBinaryTargetRunTaskProvider.Task> ID = Key.create("CreateTempScalaBinaryTarget");

  static class Task extends BeforeRunTask<Task> {
    Task() {
      super(ID);
      setEnabled(true);
    }
  }

  private final Project project;

  public ScalaGeneratedBinaryTargetRunTaskProvider(Project project) {
    this.project = project;
  }

  @Override
  public Key<Task> getId() {
    return ID;
  }

  @Override
  public Icon getIcon() {
    return BlazeIcons.Blaze;
  }

  @Override
  public Icon getTaskIcon(ScalaGeneratedBinaryTargetRunTaskProvider.Task task) {
    return BlazeIcons.Blaze;
  }

  @Override
  public String getName() {
    return "Generate ad-hoc scala binary target";
  }

  @Override
  public final boolean canExecuteTask(RunConfiguration configuration, ScalaGeneratedBinaryTargetRunTaskProvider.Task task) {
    return isValidConfiguration(configuration);
  }

  private boolean isValidConfiguration(RunConfiguration runConfiguration) {
    return runConfiguration instanceof BlazeCommandRunConfiguration;
  }

  @Nullable
  @Override
  public Task createTask(@NotNull RunConfiguration runConfiguration) {
    if (isValidConfiguration(runConfiguration)) {
      return new Task();
    }
    return null;
  }

  @Override
  public boolean executeTask(
    DataContext context,
    @NotNull RunConfiguration configuration,
    @NotNull ExecutionEnvironment env,
    @NotNull Task task) {
    BlazeCommandRunConfiguration runConfiguration = (BlazeCommandRunConfiguration)configuration;

    TargetIdeInfo target = getTargetInfo(runConfiguration.getTarget());
    if (target == null)
      return false;

    return writeTargetToDisk(target);
  }

  private TargetIdeInfo getTargetInfo(TargetExpression targetExpression) {
    FilteredTargetMap map =
      SyncCache.getInstance(project)
        .get(SCALA_BINARY_FOR_LIBS_MAP_KEY,
             ScalaLibraryRunConfigurationProducer::createBinaryTargetsMap);

    Label label = Label.create(targetExpression.toString());
    Collection<TargetIdeInfo> targets = map.targetsForLabel(label);

    return Iterables.getFirst(targets, null);
  }

  private boolean writeTargetToDisk(TargetIdeInfo target) {
    return WriteCommandAction.runWriteCommandAction(project,
      (Computable<Boolean>)
        () -> {
          WorkspaceRoot workspaceRoot = WorkspaceRoot.fromProject(project);
          File dir = workspaceRoot.fileForPath(target.key.label.blazePackage());
          try {
            VirtualFile tempDir = VfsUtil.createDirectories(dir.getPath());
            tempDir.findOrCreateChildData(this, "BUILD");
            return writeRawContent(project, target);
          } catch (IOException e) {
            e.printStackTrace();
            return false;
          }
        });
  }

  private boolean writeRawContent(Project project, TargetIdeInfo target) {
    BuildFileModifier modifier = BuildFileModifier.getInstance();
    Label targetLabel = target.key.label;

    modifier.addLoadStatement(
      project,
      targetLabel.blazePackage(),
      Label.create("@io_bazel_rules_scala//scala:scala.bzl"),
      "scala_binary");

    Label dependencyLabel = Iterables.getFirst(
      target.dependencies.stream()
        .map(d -> d.targetKey.label)
        .collect(Collectors.toList()), null);

    if (dependencyLabel == null)
      return false;

    assert target.javaIdeInfo != null;
    String mainClass = target.javaIdeInfo.javaBinaryMainClass;
    Label newRule = dependencyLabel.withTargetName(dependencyLabel.targetName() + "-main");

    if (ruleExists(project, targetLabel.blazePackage(), newRule)) {
      return true;
    }

    modifier.addRule(
      project,
      targetLabel,
      Kind.SCALA_BINARY,
      ImmutableMap.of(
        "main_class", "\"" + mainClass + "\"",
        "runtime_deps", "['" + dependencyLabel.toString() + "']")
      );

    return true;
  }

  private boolean ruleExists(Project project, WorkspacePath targetPackage, Label rule) {
    BuildFile buildFile = BuildFileModifierImpl.getBuildFile(project, targetPackage);
    if (buildFile == null) {
      return false;
    }

    return buildFile.findRule(rule.targetName().toString()) != null;
  }
}
