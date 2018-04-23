package com.google.idea.blaze.scala.run.producers;

import com.google.common.base.Joiner;
import com.google.common.collect.Iterables;
import com.google.idea.blaze.base.dependencies.TargetInfo;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.TargetExpression;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration;
import com.google.idea.blaze.base.run.testmap.FilteredTargetMap;
import com.google.idea.blaze.base.sync.SyncCache;
import com.intellij.execution.BeforeRunTask;
import com.intellij.execution.BeforeRunTaskProvider;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.CharsetToolkit;
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

import static com.google.idea.blaze.scala.run.producers.ScalaLibraryRunConfigurationProducer.*;

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
            VirtualFile buildFile = tempDir.findOrCreateChildData(this, "BUILD");
            return writeRawContent(buildFile, target);
          } catch (IOException e) {
            e.printStackTrace();
            return false;
          }
        });
  }

  private boolean writeRawContent(VirtualFile buildFile, TargetIdeInfo target) throws IOException {
    String template =
      Joiner.on(System.lineSeparator())
      .join(
        "load('@io_bazel_rules_scala//scala:scala.bzl', 'scala_binary')",
        "",
        "scala_binary(",
        "    main_class='%s',",
        "    name='%s',",
        "    runtime_deps=['%s'],",
        ")");

    Label dependencyLabel = Iterables.getFirst(
      target.dependencies.stream()
        .map(d -> d.targetKey.label)
        .collect(Collectors.toList()), null);

    if (dependencyLabel == null) {
      return false;
    }

    assert target.javaIdeInfo != null;
    String mainClass = target.javaIdeInfo.javaBinaryMainClass;
    String targetName = dependencyLabel.targetName() + "-main";
    String text = String.format(template, mainClass, targetName, dependencyLabel.toString());

    buildFile.setBinaryContent(text.getBytes(CharsetToolkit.UTF8_CHARSET));
    return true;
  }
}
