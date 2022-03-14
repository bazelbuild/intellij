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
package com.google.idea.blaze.base.bazel;

import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.MustBeClosed;
import com.google.idea.blaze.base.bazel.BazelBuildSystem.BazelBinary;
import com.google.idea.blaze.base.command.BlazeCommandRunner;
import com.google.idea.blaze.base.command.buildresult.BuildResultHelper;
import com.google.idea.blaze.base.command.buildresult.BuildResultHelperProvider;
import com.google.idea.blaze.base.command.info.BlazeInfo;
import com.google.idea.blaze.base.lang.buildfile.language.semantics.RuleDefinition;
import com.google.idea.blaze.base.model.BlazeVersionData;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.BuildBinaryType;
import com.google.idea.blaze.base.settings.BuildSystem;
import com.intellij.openapi.project.Project;
import javax.inject.Provider;
import org.jetbrains.annotations.Nullable;

/**
 * A subclass of {@link BazelBuildSystemProvider} that allows functionality to be overridden in
 * tests.
 */
public class BuildSystemProviderWrapper implements BuildSystemProvider {

  class BazelBinaryWrapper implements BazelBinary {

    private final BazelBinary inner;

    BazelBinaryWrapper(BazelBinary wrapped) {
      inner = wrapped;
    }

    @Override
    public BuildBinaryType getType() {
      return inner.getType();
    }

    @Override
    public String getPath() {
      return inner.getPath();
    }

    @Override
    public boolean supportsParallelism() {
      return inner.supportsParallelism();
    }

    @Override
    public BazelBinary setBlazeInfo(BlazeInfo blazeInfo) {
      inner.setBlazeInfo(blazeInfo);
      return this;
    }

    public void overrideBuildResultHelperProvider(BuildResultHelperProvider provider) {
      buildResultHelperProvider = provider;
    }

    @Override
    @MustBeClosed
    public BuildResultHelper createBuildResultProvider() {
      if (buildResultHelperProvider != null) {
        return buildResultHelperProvider.doCreate();
      }
      return inner.createBuildResultProvider();
    }

    @Override
    public BlazeCommandRunner getCommandRunner() {
      return inner.getCommandRunner();
    }
  }

  class BuildSystemWrapper implements BazelBuildSystem {

    private final BazelBuildSystem inner;

    BuildSystemWrapper(BazelBuildSystem wrapped) {
      inner = wrapped;
    }

    @Override
    public BuildSystem type() {
      return inner.type();
    }

    @Override
    public BazelBinaryWrapper getBinary(Project project, boolean requestParallelismSupport) {
      return new BazelBinaryWrapper(inner.getBinary(project, requestParallelismSupport));
    }

    @Override
    public SyncStrategy getSyncStrategy() {
      return inner.getSyncStrategy();
    }
  }

  private final Provider<BuildSystemProvider> innerProvider;
  private BuildSystemProvider inner;
  private BuildSystemWrapper buildSystem;
  private BuildResultHelperProvider buildResultHelperProvider;

  public BuildSystemProviderWrapper(BuildSystemProvider wrapped) {
    innerProvider = () -> wrapped;
  }

  public BuildSystemProviderWrapper(Provider<Project> projectProvider) {
    this.innerProvider =
        () -> {
          Project project = projectProvider.get();
          BuildSystem type = Blaze.getBuildSystem(project);
          for (BuildSystemProvider provider : BuildSystemProvider.EP_NAME.getExtensions()) {
            if (provider instanceof BuildSystemProviderWrapper) {
              continue;
            }
            if (provider.buildSystem() == type) {
              return provider;
            }
          }
          throw new IllegalStateException("No BuildSystemProvider found");
        };
  }

  private synchronized BuildSystemProvider inner() {
    if (inner == null) {
      inner = innerProvider.get();
      buildSystem = new BuildSystemWrapper(inner.getBuildSystem());
    }
    return inner;
  }

  @Override
  public BazelBuildSystem getBuildSystem() {
    inner();
    return buildSystem;
  }

  @Override
  public BuildSystem buildSystem() {
    return inner().buildSystem();
  }

  @Override
  public String getBinaryPath(Project project) {
    return inner().getBinaryPath(project);
  }

  @Override
  public BuildBinaryType getSyncBinaryType(boolean forceParallel) {
    return inner().getSyncBinaryType(forceParallel);
  }

  @Override
  public WorkspaceRootProvider getWorkspaceRootProvider() {
    return inner().getWorkspaceRootProvider();
  }

  @Override
  public ImmutableList<String> buildArtifactDirectories(WorkspaceRoot root) {
    return inner().buildArtifactDirectories(root);
  }

  @Nullable
  @Override
  public String getRuleDocumentationUrl(RuleDefinition rule) {
    return inner().getRuleDocumentationUrl(rule);
  }

  @Nullable
  @Override
  public String getProjectViewDocumentationUrl() {
    return inner().getProjectViewDocumentationUrl();
  }

  @Nullable
  @Override
  public String getLanguageSupportDocumentationUrl(String relativeDocName) {
    return inner().getLanguageSupportDocumentationUrl(relativeDocName);
  }

  @Override
  public ImmutableList<String> possibleBuildFileNames() {
    return inner().possibleBuildFileNames();
  }

  @Override
  public ImmutableList<String> possibleWorkspaceFileNames() {
    return inner().possibleWorkspaceFileNames();
  }

  @Override
  public void populateBlazeVersionData(
      BuildSystem buildSystem,
      WorkspaceRoot workspaceRoot,
      BlazeInfo blazeInfo,
      BlazeVersionData.Builder builder) {
    inner().populateBlazeVersionData(buildSystem, workspaceRoot, blazeInfo, builder);
  }

  public void setBuildResultHelperProvider(BuildResultHelperProvider provider) {
    buildResultHelperProvider = provider;
  }
}
