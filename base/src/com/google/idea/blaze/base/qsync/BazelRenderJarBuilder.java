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

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.bazel.BazelExitCodeException;
import com.google.idea.blaze.base.bazel.BuildSystem;
import com.google.idea.blaze.base.bazel.BuildSystem.BuildInvoker;
import com.google.idea.blaze.base.command.BlazeCommand;
import com.google.idea.blaze.base.command.BlazeCommandName;
import com.google.idea.blaze.base.command.BlazeFlags;
import com.google.idea.blaze.base.command.BlazeInvocationContext;
import com.google.idea.blaze.base.command.buildresult.BuildResultHelper;
import com.google.idea.blaze.base.command.buildresult.LocalFileOutputArtifact;
import com.google.idea.blaze.base.command.buildresult.OutputArtifact;
import com.google.idea.blaze.base.command.buildresult.RemoteOutputArtifact;
import com.google.idea.blaze.base.projectview.ProjectViewManager;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.sync.aspects.BlazeBuildOutputs;
import com.google.idea.blaze.common.Label;
import com.google.idea.blaze.exception.BuildException;
import com.intellij.openapi.project.Project;
import java.io.File;
import java.util.List;
import java.util.Set;

/** An object that knows how to build dependencies for given targets */
public class BazelRenderJarBuilder implements RenderJarBuilder {

  protected final Project project;
  protected final BuildSystem buildSystem;

  public BazelRenderJarBuilder(Project project, BuildSystem buildSystem) {
    this.project = project;
    this.buildSystem = buildSystem;
  }

  @Override
  public RenderJarInfo buildRenderJar(BlazeContext context, Set<Label> buildTargets)
      throws BuildException {
    BuildInvoker invoker = buildSystem.getDefaultInvoker(project, context);
    try (BuildResultHelper buildResultHelper = invoker.createBuildResultHelper()) {
      ProjectViewSet projectViewSet = ProjectViewManager.getInstance(project).getProjectViewSet();
      List<String> additionalBlazeFlags =
          BlazeFlags.blazeFlags(
              project,
              projectViewSet,
              BlazeCommandName.BUILD,
              context,
              BlazeInvocationContext.OTHER_CONTEXT);

      // TODO(b/283283123): Refactor render jar functionality to build_dependencies.bzl
      BlazeCommand.Builder builder =
          BlazeCommand.builder(invoker, BlazeCommandName.BUILD)
              .addBlazeFlags(buildTargets.stream().map(Label::toString).collect(toImmutableList()))
              .addBlazeFlags(buildResultHelper.getBuildFlags())
              .addBlazeFlags(additionalBlazeFlags)
              .addBlazeFlags(
                  "--aspects=//java/com/google/devtools/intellij/blaze/plugin/aspect:intellij_info.bzl%intellij_info_aspect")
              .addBlazeFlags("--output_groups=intellij-render-resolve-android");

      BlazeBuildOutputs outputs =
          invoker.getCommandRunner().run(project, builder, buildResultHelper, context);
      BazelExitCodeException.throwIfFailed(builder, outputs.buildResult);

      return createRenderJarInfo(outputs);
    }
  }

  private RenderJarInfo createRenderJarInfo(BlazeBuildOutputs blazeBuildOutputs) {
    ImmutableList<OutputArtifact> allRenderJars =
        translateOutputArtifacts(
            blazeBuildOutputs.getOutputGroupArtifacts(
                s -> s.contains("intellij-render-resolve-android")));
    // TODO(b/283283123): Update the aspect to only return the render jar of the required target.
    // TODO(b/283280194): To setup fqcn -> target and target -> render jar mappings that would
    // increase the count of render jars but help with the performance by reducing the size of the
    // render jar loaded by the class loader.
    // The last render jar in the list is the render jar generated for the required target
    ImmutableList<OutputArtifact> renderJars =
        allRenderJars.size() > 0
            ? ImmutableList.of(allRenderJars.get(allRenderJars.size() - 1))
            : ImmutableList.of();
    return RenderJarInfo.create(renderJars, blazeBuildOutputs.buildResult.exitCode);
  }

  private ImmutableList<OutputArtifact> translateOutputArtifacts(
      ImmutableList<OutputArtifact> artifacts) {
    return artifacts.stream()
        .map(BazelRenderJarBuilder::translateOutputArtifact)
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
        srcfsArtifact, it.getPath(), it.getConfigurationMnemonic(), it.getDigest());
  }

}
