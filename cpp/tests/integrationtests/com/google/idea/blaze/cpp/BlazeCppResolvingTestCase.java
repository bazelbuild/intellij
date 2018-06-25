/*
 * Copyright 2018 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.cpp;

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.ideinfo.CIdeInfo;
import com.google.idea.blaze.base.ideinfo.CToolchainIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetMap;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.MockBlazeProjectDataBuilder;
import com.google.idea.blaze.base.model.MockBlazeProjectDataManager;
import com.google.idea.blaze.base.model.primitives.ExecutionRootPath;
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.model.primitives.TargetExpression;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.projectview.ProjectView;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.projectview.section.ListSection;
import com.google.idea.blaze.base.projectview.section.sections.DirectoryEntry;
import com.google.idea.blaze.base.projectview.section.sections.DirectorySection;
import com.google.idea.blaze.base.projectview.section.sections.TargetSection;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.cidr.lang.psi.OCFile;
import com.jetbrains.cidr.lang.symbols.symtable.FileSymbolTablesCache;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Before;

/**
 * C++ test cases which require resolving an {@link com.jetbrains.cidr.lang.workspace.OCWorkspace}
 * based on a given project view.
 */
public class BlazeCppResolvingTestCase extends BlazeCppIntegrationTestCase {

  private VirtualFile genfilesRoot;

  @Before
  public void setupHeaderRootsAndCompilerVersion() {
    createHeaderRoots();
    registerApplicationService(
        CompilerVersionChecker.class, new MockCompilerVersionChecker("1234"));
  }

  // Assumes tests want some representative non-workspace header search roots.
  protected void createHeaderRoots() {
    genfilesRoot = fileSystem.createDirectory("output/genfiles");
    workspace.createDirectory(new WorkspacePath("include/third_party/libxml/_/libxml"));
    workspace.createDirectory(new WorkspacePath("third_party/stl"));
    workspace.createDirectory(new WorkspacePath("third_party/toolchain/include/c++/4.9"));
  }

  protected TargetIdeInfo.Builder createCcTarget(
      String label, Kind kind, List<ArtifactLocation> sources, List<ArtifactLocation> headers) {
    TargetIdeInfo.Builder targetInfo =
        TargetIdeInfo.builder().setLabel(label).setKind(kind).addDependency("//:toolchain");
    sources.forEach(targetInfo::addSource);
    return targetInfo.setCInfo(
        CIdeInfo.builder()
            .addSources(sources)
            .addHeaders(headers)
            .addTransitiveIncludeDirectories(
                ImmutableList.of(new ExecutionRootPath("include/third_party/libxml/_/libxml")))
            .addTransitiveQuoteIncludeDirectories(
                ImmutableList.of(
                    new ExecutionRootPath("."),
                    new ExecutionRootPath(VfsUtilCore.virtualToIoFile(genfilesRoot))))
            .addTransitiveSystemIncludeDirectories(
                ImmutableList.of(new ExecutionRootPath("third_party/stl"))));
  }

  protected static TargetIdeInfo.Builder createCcToolchain() {
    return TargetIdeInfo.builder()
        .setLabel("//:toolchain")
        .setKind(Kind.CC_TOOLCHAIN)
        .setCToolchainInfo(
            CToolchainIdeInfo.builder()
                .setCppExecutable(new ExecutionRootPath("cc"))
                .addBuiltInIncludeDirectories(
                    ImmutableList.of(
                        new ExecutionRootPath("third_party/toolchain/include/c++/4.9"))));
  }

  protected static List<ArtifactLocation> sources(String... paths) {
    return Arrays.stream(paths)
        .map(path -> ArtifactLocation.builder().setRelativePath(path).setIsSource(true).build())
        .collect(Collectors.toList());
  }

  protected static ListSection<DirectoryEntry> directories(String... directories) {
    return ListSection.builder(DirectorySection.KEY)
        .addAll(
            Arrays.stream(directories)
                .map(directory -> DirectoryEntry.include(new WorkspacePath(directory)))
                .collect(Collectors.toList()))
        .build();
  }

  protected static ListSection<TargetExpression> targets(String... targets) {
    return ListSection.builder(TargetSection.KEY)
        .addAll(
            Arrays.stream(targets)
                .map(TargetExpression::fromStringSafe)
                .collect(Collectors.toList()))
        .build();
  }

  protected static ProjectView projectView(
      ListSection<DirectoryEntry> directories, ListSection<TargetExpression> targets) {
    return ProjectView.builder().add(directories).add(targets).build();
  }

  protected MockBlazeProjectDataBuilder projectDataBuilder() {
    return MockBlazeProjectDataBuilder.builder(workspaceRoot)
        .setOutputBase(fileSystem.getRootDir() + "/output");
  }

  protected void resolve(ProjectView projectView, TargetMap targetMap, OCFile... files) {
    resolve(projectView, projectDataBuilder().setTargetMap(targetMap).build(), files);
  }

  protected void resolve(
      ProjectView projectView, BlazeProjectData blazeProjectData, OCFile... files) {
    registerProjectService(
        BlazeProjectDataManager.class, new MockBlazeProjectDataManager(blazeProjectData));
    BlazeCWorkspace workspace = BlazeCWorkspace.getInstance(getProject());
    BlazeConfigurationResolverResult newResult =
        workspace.resolveConfigurations(
            new BlazeContext(),
            workspaceRoot,
            ProjectViewSet.builder().add(projectView).build(),
            blazeProjectData);
    BlazeCWorkspace.CommitableConfiguration config =
        workspace.calculateConfigurations(blazeProjectData, workspaceRoot, newResult, null);
    workspace.commitConfigurations(config);

    for (OCFile file : files) {
      resetFileSymbols(file);
    }
    FileSymbolTablesCache.getInstance(getProject()).ensurePendingFilesProcessed();
  }

  private void resetFileSymbols(OCFile file) {
    FileSymbolTablesCache.getInstance(getProject()).handleFileChange(file, true);
  }
}
