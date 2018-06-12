/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
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

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.ideinfo.CIdeInfo;
import com.google.idea.blaze.base.ideinfo.CToolchainIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetMap;
import com.google.idea.blaze.base.ideinfo.TargetMapBuilder;
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
import com.jetbrains.cidr.lang.psi.OCReferenceElement;
import com.jetbrains.cidr.lang.quickfixes.OCImportSymbolFix;
import com.jetbrains.cidr.lang.symbols.symtable.FileSymbolTablesCache;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests that {@link BlazeCppAutoImportHelper} is able to get the correct form of #include for the
 * {@link OCImportSymbolFix} quickfix, given typical workspace layouts / location of system headers.
 */
@RunWith(JUnit4.class)
public class BlazeCppAutoImportHelperTest extends BlazeCppIntegrationTestCase {

  private VirtualFile genfilesRoot;

  @Before
  public void setup() {
    createHeaderRoots();
    registerApplicationService(
        CompilerVersionChecker.class, new MockCompilerVersionChecker("1234"));
  }

  @Test
  public void stlPathsUnderWorkspaceRoot_importStlHeader() {
    ProjectView projectView = projectView(directories("foo/bar"), targets("//foo/bar:bar"));
    TargetMap targetMap =
        TargetMapBuilder.builder()
            .addTarget(createCcToolchain())
            .addTarget(
                createCcTarget(
                    "//foo/bar:bar", Kind.CC_LIBRARY, sources("foo/bar/bar.cc"), sources()))
            .addTarget(
                createCcTarget(
                    "//third_party/stl:stl",
                    Kind.CC_LIBRARY,
                    sources(),
                    sources("third_party/stl/vector.h")))
            .build();
    // Normally this is <vector> without .h, but we need to trick the file type detector into
    // realizing that this is an OCFile.
    OCFile header =
        createFile(
            "third_party/stl/vector.h",
            "namespace std {",
            "template<typename T> class vector {};",
            "}");
    OCFile file = createFile("foo/bar/bar.cc", "std::vector<int> my_vector;");

    resolve(projectView, targetMap, file, header);

    testFixture.openFileInEditor(file.getVirtualFile());
    OCReferenceElement referenceElement =
        testFixture.findElementByText("std::vector<int>", OCReferenceElement.class);
    OCImportSymbolFix fix = new OCImportSymbolFix(referenceElement);
    assertThat(fix.isAvailable(getProject(), testFixture.getEditor(), file)).isTrue();
    OCImportSymbolFix.AutoImportItem importItem =
        Iterables.getOnlyElement(fix.getAutoImportItems());
    assertThat(importItem.getTitleAndLocation().getFirst()).isEqualTo("class 'std::vector'");
    assertThat(importItem.getTitleAndLocation().getSecond()).isEqualTo("<vector.h>");
  }

  @Test
  public void sameDirectory_importUserHeader() {
    ProjectView projectView = projectView(directories("foo/bar"), targets("//foo/bar:bar"));
    TargetMap targetMap =
        TargetMapBuilder.builder()
            .addTarget(createCcToolchain())
            .addTarget(
                createCcTarget(
                    "//foo/bar:bar",
                    Kind.CC_LIBRARY,
                    sources("foo/bar/bar.cc"),
                    sources("foo/bar/test.h")))
            .build();
    OCFile header = createFile("foo/bar/test.h", "class SomeClass {};");
    OCFile file = createFile("foo/bar/bar.cc", "SomeClass* my_class = new SomeClass();");

    resolve(projectView, targetMap, file, header);

    testFixture.openFileInEditor(file.getVirtualFile());
    OCReferenceElement referenceElement =
        testFixture.findElementByText("SomeClass*", OCReferenceElement.class);
    OCImportSymbolFix fix = new OCImportSymbolFix(referenceElement);
    assertThat(fix.isAvailable(getProject(), testFixture.getEditor(), file)).isTrue();
    OCImportSymbolFix.AutoImportItem importItem =
        Iterables.getOnlyElement(fix.getAutoImportItems());
    assertThat(importItem.getTitleAndLocation().getFirst()).isEqualTo("class 'SomeClass'");
    assertThat(importItem.getTitleAndLocation().getSecond()).isEqualTo("\"foo/bar/test.h\"");
  }

  @Test
  public void differentDirectory_importUserHeader() {
    ProjectView projectView =
        projectView(directories("foo/bar", "baz"), targets("//foo/bar", "//baz"));
    TargetMap targetMap =
        TargetMapBuilder.builder()
            .addTarget(createCcToolchain())
            .addTarget(
                createCcTarget(
                    "//foo/bar:bar", Kind.CC_LIBRARY, sources("foo/bar/bar.cc"), sources()))
            .addTarget(
                createCcTarget("//baz:baz", Kind.CC_LIBRARY, sources(""), sources("baz/test.h")))
            .build();
    OCFile header = createFile("baz/test.h", "class SomeClass {};");
    OCFile file = createFile("foo/bar/bar.cc", "SomeClass* my_class = new SomeClass();");

    resolve(projectView, targetMap, file, header);

    testFixture.openFileInEditor(file.getVirtualFile());
    OCReferenceElement referenceElement =
        testFixture.findElementByText("SomeClass*", OCReferenceElement.class);
    OCImportSymbolFix fix = new OCImportSymbolFix(referenceElement);
    assertThat(fix.isAvailable(getProject(), testFixture.getEditor(), file)).isTrue();
    OCImportSymbolFix.AutoImportItem importItem =
        Iterables.getOnlyElement(fix.getAutoImportItems());
    assertThat(importItem.getTitleAndLocation().getFirst()).isEqualTo("class 'SomeClass'");
    assertThat(importItem.getTitleAndLocation().getSecond()).isEqualTo("\"baz/test.h\"");
  }

  @Test
  public void importGenfile_relativeToOutputBase() {
    ProjectView projectView = projectView(directories("foo/bar"), targets("//foo/bar:bar"));
    TargetMap targetMap =
        TargetMapBuilder.builder()
            .addTarget(createCcToolchain())
            .addTarget(
                createCcTarget(
                    "//foo/bar:bar", Kind.CC_LIBRARY, sources("foo/bar/bar.cc"), sources()))
            .build();
    OCFile header =
        createNonWorkspaceFile("output/genfiles/foo/bar/test.proto.h", "class SomeClass {};");
    OCFile file = createFile("foo/bar/bar.cc", "SomeClass* my_class = new SomeClass();");

    resolve(projectView, targetMap, file, header);

    testFixture.openFileInEditor(file.getVirtualFile());
    OCReferenceElement referenceElement =
        testFixture.findElementByText("SomeClass*", OCReferenceElement.class);
    OCImportSymbolFix fix = new OCImportSymbolFix(referenceElement);
    assertThat(fix.isAvailable(getProject(), testFixture.getEditor(), file)).isTrue();
    OCImportSymbolFix.AutoImportItem importItem =
        Iterables.getOnlyElement(fix.getAutoImportItems());
    assertThat(importItem.getTitleAndLocation().getFirst()).isEqualTo("class 'SomeClass'");
    assertThat(importItem.getTitleAndLocation().getSecond()).isEqualTo("\"foo/bar/test.proto.h\"");
  }

  @Test
  public void importGenfileInNewFile_relativeToOutputBase() {
    ProjectView projectView = projectView(directories("foo/bar"), targets("//foo/bar:bar"));
    TargetMap targetMap =
        TargetMapBuilder.builder()
            .addTarget(createCcToolchain())
            .addTarget(
                createCcTarget(
                    "//foo/bar:bar", Kind.CC_LIBRARY, sources("foo/bar/bar.cc"), sources()))
            .build();
    OCFile header =
        createNonWorkspaceFile("output/genfiles/foo/bar/test.proto.h", "class SomeClass {};");
    // Create some .cc file to create a config...
    OCFile fileWithConfig =
        createFile(
            "foo/bar/bar.cc",
            "#include \"foo/bar/test.proto.h\"",
            "SomeClass* my_class = new SomeClass();");
    resolve(projectView, targetMap, fileWithConfig, header);

    // But test against a new .cc file, which hopefully falls back to an existing config, and
    // will have some basic header search roots (like genfiles).
    OCFile newFile = createFile("foo/bar/new_file.cc", "SomeClass* my_class = new SomeClass();");
    testFixture.openFileInEditor(newFile.getVirtualFile());
    OCReferenceElement referenceElement =
        testFixture.findElementByText("SomeClass*", OCReferenceElement.class);
    OCImportSymbolFix fix = new OCImportSymbolFix(referenceElement);
    assertThat(fix.isAvailable(getProject(), testFixture.getEditor(), fileWithConfig)).isTrue();
    OCImportSymbolFix.AutoImportItem importItem =
        Iterables.getOnlyElement(fix.getAutoImportItems());
    assertThat(importItem.getTitleAndLocation().getFirst()).isEqualTo("class 'SomeClass'");
    assertThat(importItem.getTitleAndLocation().getSecond()).isEqualTo("\"foo/bar/test.proto.h\"");
  }

  private static List<ArtifactLocation> sources(String... paths) {
    return Arrays.stream(paths)
        .map(path -> ArtifactLocation.builder().setRelativePath(path).setIsSource(true).build())
        .collect(Collectors.toList());
  }

  private void createHeaderRoots() {
    genfilesRoot = fileSystem.createDirectory("output/genfiles");
    workspace.createDirectory(new WorkspacePath("include/third_party/libxml/_/libxml"));
    workspace.createDirectory(new WorkspacePath("third_party/stl"));
    workspace.createDirectory(new WorkspacePath("third_party/lib_that_expects_angle_include"));
    workspace.createDirectory(new WorkspacePath("third_party/toolchain/include/c++/4.9"));
  }

  private TargetIdeInfo.Builder createCcTarget(
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
                ImmutableList.of(
                    new ExecutionRootPath("third_party/stl"),
                    new ExecutionRootPath("third_party/lib_that_expects_angle_include"))));
  }

  private static TargetIdeInfo.Builder createCcToolchain() {
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

  private static ListSection<DirectoryEntry> directories(String... directories) {
    return ListSection.builder(DirectorySection.KEY)
        .addAll(
            Arrays.stream(directories)
                .map(directory -> DirectoryEntry.include(WorkspacePath.createIfValid(directory)))
                .collect(Collectors.toList()))
        .build();
  }

  private static ListSection<TargetExpression> targets(String... targets) {
    return ListSection.builder(TargetSection.KEY)
        .addAll(
            Arrays.stream(targets)
                .map(TargetExpression::fromStringSafe)
                .collect(Collectors.toList()))
        .build();
  }

  private static ProjectView projectView(
      ListSection<DirectoryEntry> directories, ListSection<TargetExpression> targets) {
    return ProjectView.builder().add(directories).add(targets).build();
  }

  private MockBlazeProjectDataBuilder projectDataBuilder() {
    return MockBlazeProjectDataBuilder.builder(workspaceRoot)
        .setOutputBase(fileSystem.getRootDir() + "/output");
  }

  private void resolve(ProjectView projectView, TargetMap targetMap, OCFile... files) {
    BlazeProjectData blazeProjectData = projectDataBuilder().setTargetMap(targetMap).build();
    registerProjectService(
        BlazeProjectDataManager.class, new MockBlazeProjectDataManager(blazeProjectData));
    BlazeCWorkspace workspace = BlazeCWorkspace.getInstance(getProject());
    BlazeConfigurationResolverResult newResult =
        workspace.resolveConfigurations(
            new BlazeContext(),
            workspaceRoot,
            ProjectViewSet.builder().add(projectView).build(),
            blazeProjectData,
            null);
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
