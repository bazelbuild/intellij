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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.joining;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.devtools.intellij.qsync.ArtifactTrackerData.BuildArtifacts;
import com.google.idea.blaze.base.bazel.BazelExitCodeException;
import com.google.idea.blaze.base.bazel.BazelExitCodeException.ThrowOption;
import com.google.idea.blaze.base.bazel.BuildSystem;
import com.google.idea.blaze.base.bazel.BuildSystem.BuildInvoker;
import com.google.idea.blaze.base.command.BlazeCommand;
import com.google.idea.blaze.base.command.BlazeCommandName;
import com.google.idea.blaze.base.command.BlazeFlags;
import com.google.idea.blaze.base.command.BlazeInvocationContext;
import com.google.idea.blaze.base.command.buildresult.BlazeArtifact;
import com.google.idea.blaze.base.command.buildresult.BuildResultHelper;
import com.google.idea.blaze.base.command.buildresult.LocalFileOutputArtifact;
import com.google.idea.blaze.base.command.buildresult.OutputArtifact;
import com.google.idea.blaze.base.command.buildresult.RemoteOutputArtifact;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.projectview.ProjectViewManager;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.sync.aspects.BlazeBuildOutputs;
import com.google.idea.blaze.common.Label;
import com.google.idea.blaze.exception.BuildException;
import com.google.idea.blaze.qsync.BlazeQueryParser;
import com.google.idea.blaze.qsync.project.ProjectDefinition;
import com.google.protobuf.TextFormat;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.project.Project;
import java.io.File;
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
public class BazelDependencyBuilder implements DependencyBuilder {

  protected final Project project;
  protected final BuildSystem buildSystem;
  protected final ProjectDefinition projectDefinition;
  protected final WorkspaceRoot workspaceRoot;
  protected final ImmutableSet<String> handledRuleKinds;

  public BazelDependencyBuilder(
      Project project,
      BuildSystem buildSystem,
      ProjectDefinition projectDefinition,
      WorkspaceRoot workspaceRoot,
      ImmutableSet<String> handledRuleKinds) {
    this.project = project;
    this.buildSystem = buildSystem;
    this.projectDefinition = projectDefinition;
    this.workspaceRoot = workspaceRoot;
    this.handledRuleKinds = handledRuleKinds;
  }

  @Override
  public OutputInfo build(BlazeContext context, Set<Label> buildTargets)
      throws IOException, BuildException {
    BuildInvoker invoker = buildSystem.getDefaultInvoker(project, context);
    try (BuildResultHelper buildResultHelper = invoker.createBuildResultHelper()) {
      String includes =
          projectDefinition.projectIncludes().stream()
              .map(path -> "//" + path)
              .collect(joining(","));
      String excludes =
          projectDefinition.projectExcludes().stream()
              .map(path -> "//" + path)
              .collect(joining(","));
      String aspectLocation = prepareAspect(context);
      Set<String> ruleKindsToBuild =
          Sets.difference(BlazeQueryParser.ALWAYS_BUILD_RULE_KINDS, handledRuleKinds);
      String alwaysBuildParam = Joiner.on(",").join(ruleKindsToBuild);

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

      BlazeCommand.Builder builder =
          BlazeCommand.builder(invoker, BlazeCommandName.BUILD)
              .addBlazeFlags(buildTargets.stream().map(Label::toString).collect(toImmutableList()))
              .addBlazeFlags(buildResultHelper.getBuildFlags())
              .addBlazeFlags(additionalBlazeFlags)
              .addBlazeFlags(
                  String.format(
                      "--aspects=%1$s%%collect_dependencies,%1$s%%package_dependencies",
                      aspectLocation))
              .addBlazeFlags(String.format("--aspects_parameters=include=%s", includes))
              .addBlazeFlags(String.format("--aspects_parameters=exclude=%s", excludes))
              .addBlazeFlags(
                  String.format("--aspects_parameters=always_build_rules=%s", alwaysBuildParam))
              .addBlazeFlags("--aspects_parameters=generate_aidl_classes=True")
              .addBlazeFlags("--output_groups=qsync_jars")
              .addBlazeFlags("--output_groups=qsync_aars")
              .addBlazeFlags("--output_groups=qsync_gensrcs")
              .addBlazeFlags("--output_groups=artifact_info_file")
              .addBlazeFlags("--noexperimental_run_validations")
              .addBlazeFlags("--keep_going");

      BlazeBuildOutputs outputs =
          invoker.getCommandRunner().run(project, builder, buildResultHelper, context);
      BazelExitCodeException.throwIfFailed(
          builder,
          outputs.buildResult,
          ThrowOption.ALLOW_PARTIAL_SUCCESS,
          ThrowOption.ALLOW_BUILD_FAILURE);

      return createOutputInfo(outputs);
    }
  }

  protected Path getBundledAspectPath() {
    PluginDescriptor plugin = checkNotNull(PluginManager.getPluginByClass(getClass()));
    return Paths.get(plugin.getPluginPath().toString(), "aspect", "build_dependencies.bzl");
  }

  /**
   * Prepares for use, and returns the location of the {@code build_dependencies.bzl} aspect.
   *
   * <p>The return value is a string in the format expected by bazel for an aspect file, omitting
   * the name of the aspect within that file. For example, {@code //package:aspect.bzl}.
   */
  protected String prepareAspect(BlazeContext context) throws IOException, BuildException {
    Path aspect = getBundledAspectPath();
    Files.copy(
        aspect,
        workspaceRoot.directory().toPath().resolve(".aswb.bzl"),
        StandardCopyOption.REPLACE_EXISTING);
    return "//:.aswb.bzl";
  }

  private OutputInfo createOutputInfo(BlazeBuildOutputs blazeBuildOutputs) throws BuildException {
    ImmutableList<OutputArtifact> jars =
        translateOutputArtifacts(
            blazeBuildOutputs.getOutputGroupArtifacts(s -> s.contains("qsync_jars")));
    ImmutableList<OutputArtifact> aars =
        translateOutputArtifacts(
            blazeBuildOutputs.getOutputGroupArtifacts(s -> s.contains("qsync_aars")));
    ImmutableList<OutputArtifact> generatedSources =
        translateOutputArtifacts(
            blazeBuildOutputs.getOutputGroupArtifacts(s -> s.contains("qsync_gensrcs")));
    ImmutableList<OutputArtifact> artifactInfoFiles =
        translateOutputArtifacts(
            blazeBuildOutputs.getOutputGroupArtifacts(s -> s.contains("artifact_info_file")));
    ImmutableSet.Builder<BuildArtifacts> artifactInfoFilesBuilder = ImmutableSet.builder();
    for (OutputArtifact artifactInfoFile : artifactInfoFiles) {
      artifactInfoFilesBuilder.add(readArtifactInfoFile(artifactInfoFile));
    }
    return OutputInfo.create(
        artifactInfoFilesBuilder.build(),
        jars,
        aars,
        generatedSources,
        blazeBuildOutputs.getTargetsWithErrors().stream()
            .map(Object::toString)
            .map(Label::of)
            .collect(toImmutableSet()),
        blazeBuildOutputs.buildResult.exitCode);
  }

  private ImmutableList<OutputArtifact> translateOutputArtifacts(
      ImmutableList<OutputArtifact> artifacts) {
    return artifacts.stream()
        .map(BazelDependencyBuilder::translateOutputArtifact)
        .collect(ImmutableList.toImmutableList());
  }

  private static OutputArtifact translateOutputArtifact(OutputArtifact it) {
    if (!(it instanceof RemoteOutputArtifact)) {
      return it;
    }
    RemoteOutputArtifact remoteOutputArtifact = (RemoteOutputArtifact) it;
    String hashId = remoteOutputArtifact.getHashId();
    if (!(hashId.startsWith("/google_src") || hashId.startsWith("/google/src"))) {
      return it;
    }
    File srcfsArtifact = new File(hashId.replaceFirst("/google_src", "/google/src"));
    return new LocalFileOutputArtifact(
        srcfsArtifact, it.getRelativePath(), it.getConfigurationMnemonic(), it.getDigest());
  }

  private BuildArtifacts readArtifactInfoFile(BlazeArtifact file) throws BuildException {
    try (InputStream inputStream = file.getInputStream()) {
      BuildArtifacts.Builder builder = BuildArtifacts.newBuilder();
      TextFormat.Parser parser = TextFormat.Parser.newBuilder().build();
      parser.merge(new InputStreamReader(inputStream, UTF_8), builder);
      return builder.build();
    } catch (IOException e) {
      throw new BuildException(e);
    }
  }

  private static String directoryToLabel(WorkspacePath directory) {
    return String.format("//%s", directory);
  }
}
