/*
 * Copyright 2022 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.qsync;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.joining;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.devtools.intellij.qsync.ArtifactTrackerData;
import com.google.idea.blaze.base.async.process.ExternalTask;
import com.google.idea.blaze.base.async.process.LineProcessingOutputStream;
import com.google.idea.blaze.base.bazel.BuildSystem;
import com.google.idea.blaze.base.bazel.BuildSystem.BuildInvoker;
import com.google.idea.blaze.base.command.BlazeCommand;
import com.google.idea.blaze.base.command.BlazeCommandName;
import com.google.idea.blaze.base.command.BlazeFlags;
import com.google.idea.blaze.base.command.BlazeInvocationContext;
import com.google.idea.blaze.base.command.buildresult.BlazeArtifact;
import com.google.idea.blaze.base.command.buildresult.BuildResultHelper;
import com.google.idea.blaze.base.command.buildresult.BuildResultHelper.GetArtifactsException;
import com.google.idea.blaze.base.command.buildresult.OutputArtifact;
import com.google.idea.blaze.base.command.buildresult.ParsedBepOutput;
import com.google.idea.blaze.base.console.BlazeConsoleLineProcessorProvider;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.projectview.ProjectViewManager;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.sync.aspects.strategy.AspectStrategy;
import com.google.idea.blaze.base.sync.projectview.ImportRoots;
import com.google.idea.blaze.common.Label;
import com.google.idea.blaze.qsync.BlazeQueryParser;
import com.google.protobuf.TextFormat;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.openapi.project.Project;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Set;

/** An object that knows how to build dependencies for given targets */
public class BazelBinaryDependencyBuilder implements DependencyBuilder {

  private final Project project;

  public BazelBinaryDependencyBuilder(Project project) {
    this.project = project;
  }

  @Override
  public OutputInfo build(
      BlazeContext context,
      Set<Label> buildTargets,
      ImportRoots importRoots,
      WorkspaceRoot workspaceRoot)
      throws IOException, GetArtifactsException {
    BuildSystem buildSystem = Blaze.getBuildSystemProvider(project).getBuildSystem();
    BuildInvoker invoker = buildSystem.getDefaultInvoker(project, context);
    try (BuildResultHelper buildResultHelper = invoker.createBuildResultHelper()) {

      IdeaPluginDescriptor plugin =
          PluginManager.getPlugin(
              PluginManager.getPluginByClassName(AspectStrategy.class.getName()));
      Path aspect = Paths.get(plugin.getPath().toString(), "aspect", "build_dependencies.bzl");
      String includes =
          importRoots.rootDirectories().stream()
              .map(BazelBinaryDependencyBuilder::directoryToLabel)
              .collect(joining(","));
      String excludes =
          importRoots.excludeDirectories().stream()
              .map(BazelBinaryDependencyBuilder::directoryToLabel)
              .collect(joining(","));
      Files.copy(
          aspect,
          workspaceRoot.directory().toPath().resolve(".aswb.bzl"),
          StandardCopyOption.REPLACE_EXISTING);
      String alwaysBuildRuleTypes = Joiner.on(",").join(BlazeQueryParser.ALWAYS_BUILD_RULE_TYPES);

      ProjectViewSet projectViewSet = ProjectViewManager.getInstance(project).getProjectViewSet();
      // TODO This is not SYNC_CONTEXT, but also not OTHER_CONTEXT, we need to decide what kind
      // of flags need to be passed here.
      List<String> additionalBlazeFlags =
          BlazeFlags.blazeFlags(
              project,
              projectViewSet,
              BlazeCommandName.BUILD,
              context,
              BlazeInvocationContext.OTHER_CONTEXT);

      BlazeCommand builder =
          BlazeCommand.builder(invoker, BlazeCommandName.BUILD)
              .addBlazeFlags(buildTargets.stream().map(Label::toString).collect(toImmutableList()))
              .addBlazeFlags(buildResultHelper.getBuildFlags())
              .addBlazeFlags(additionalBlazeFlags)
              .addBlazeFlags(
                  "--aspects=//:.aswb.bzl%collect_dependencies,//:.aswb.bzl%package_dependencies")
              .addBlazeFlags(String.format("--aspects_parameters=include=%s", includes))
              .addBlazeFlags(String.format("--aspects_parameters=exclude=%s", excludes))
              .addBlazeFlags(
                  String.format("--aspects_parameters=always_build_rules=%s", alwaysBuildRuleTypes))
              .addBlazeFlags("--aspects_parameters=generate_aidl_classes=True")
              .addBlazeFlags("--output_groups=qsync_jars")
              .addBlazeFlags("--output_groups=qsync_aars")
              .addBlazeFlags("--output_groups=qsync_gensrcs")
              .addBlazeFlags("--output_groups=artifact_info_file")
              .addBlazeFlags("--noexperimental_run_validations")
              .build();

      LineProcessingOutputStream lpos =
          LineProcessingOutputStream.of(
              BlazeConsoleLineProcessorProvider.getAllStderrLineProcessors(context));
      ExternalTask.builder(workspaceRoot)
          .addBlazeCommand(builder)
          .context(context)
          .stdout(lpos)
          .stderr(lpos)
          .build()
          .run();
      return createOutputInfo(buildResultHelper.getBuildOutput());
    }
  }

  private OutputInfo createOutputInfo(ParsedBepOutput parsedBepOutput) throws IOException {
    ImmutableList<OutputArtifact> jars = parsedBepOutput.getOutputGroupArtifacts("qsync_jars");
    ImmutableList<OutputArtifact> aars = parsedBepOutput.getOutputGroupArtifacts("qsync_aars");
    ImmutableList<OutputArtifact> generatedSources =
        parsedBepOutput.getOutputGroupArtifacts("qsync_gensrcs");
    ImmutableList<OutputArtifact> artifactInfoFiles =
        parsedBepOutput.getOutputGroupArtifacts("artifact_info_file");
    ImmutableSet.Builder<ArtifactTrackerData.TargetToDeps> artifactInfoFilesBuilder =
        ImmutableSet.builder();
    for (OutputArtifact artifactInfoFile : artifactInfoFiles) {
      artifactInfoFilesBuilder.add(readArtifactInfoFile(artifactInfoFile));
    }
    return OutputInfo.create(artifactInfoFilesBuilder.build(), jars, aars, generatedSources);
  }

  private ArtifactTrackerData.TargetToDeps readArtifactInfoFile(BlazeArtifact file)
      throws IOException {
    try (InputStream inputStream = file.getInputStream()) {
      ArtifactTrackerData.TargetToDeps.Builder builder =
          ArtifactTrackerData.TargetToDeps.newBuilder();
      TextFormat.Parser parser = TextFormat.Parser.newBuilder().build();
      parser.merge(new InputStreamReader(inputStream, UTF_8), builder);
      return builder.build();
    }
  }

  private static String directoryToLabel(WorkspacePath directory) {
    return String.format("//%s", directory);
  }
}
