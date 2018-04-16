package com.google.idea.blaze.scala.run.producers;

import com.google.idea.blaze.base.buildmodifier.BuildFileModifier;
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.output.PrintOutput;
import com.intellij.execution.BeforeRunTask;
import com.intellij.execution.BeforeRunTaskProvider;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.history.LocalHistory;
import com.intellij.history.LocalHistoryAction;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import icons.BlazeIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.scalameta.logger;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.util.Optional;

public class ScalaGeneratedBinaryTargetRunTaskProvider
  extends BeforeRunTaskProvider<ScalaGeneratedBinaryTargetRunTaskProvider.Task> {
  public static final Key<ScalaGeneratedBinaryTargetRunTaskProvider.Task> ID = Key.create("CreateTempScalaBinaryTarget");

  static class Task extends BeforeRunTask<Task> {
    protected Task() {
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
    BlazeCommandRunConfiguration runConfiguration = (BlazeCommandRunConfiguration) configuration;
    writeFileToDisk(project,
      Label.create(runConfiguration.getTarget().toString()),
      Kind.SCALA_BINARY);
    return true;
  }

  private void writeFileToDisk(
    Project project, Label newRule, Kind ruleKind) {

    new WriteCommandAction<Optional<VirtualFile>>(project, "Create temporary BUILD file.") {
      @Override
      protected void run(@NotNull Result<Optional<VirtualFile>> result) {
        WorkspaceRoot workspaceRoot = WorkspaceRoot.fromProject(project);
        File dir = workspaceRoot.fileForPath(newRule.blazePackage());
        try {
          VirtualFile newDirectory = VfsUtil.createDirectories(dir.getPath());
          newDirectory.findOrCreateChildData(this, "BUILD");

          BuildFileModifier buildFileModifier = BuildFileModifier.getInstance();
          buildFileModifier.addRule(project, newRule, ruleKind);
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
    }.execute();
  }
}
