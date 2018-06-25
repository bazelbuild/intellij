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

import com.google.common.collect.Iterables;
import com.google.idea.blaze.base.ideinfo.TargetMap;
import com.google.idea.blaze.base.ideinfo.TargetMapBuilder;
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.projectview.ProjectView;
import com.jetbrains.cidr.lang.psi.OCFile;
import com.jetbrains.cidr.lang.psi.OCReferenceElement;
import com.jetbrains.cidr.lang.quickfixes.OCImportSymbolFix;
import com.jetbrains.cidr.lang.quickfixes.OCImportSymbolFix.AutoImportItem;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests that {@link BlazeCppAutoImportHelper} is able to get the correct form of #include for the
 * {@link OCImportSymbolFix} quickfix, given typical workspace layouts / location of system headers.
 */
@RunWith(JUnit4.class)
public class BlazeCppAutoImportHelperTest extends BlazeCppResolvingTestCase {

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

    AutoImportItem importItem = getAutoImportItem(file, "std::vector<int>");
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

    AutoImportItem importItem = getAutoImportItem(file, "SomeClass*");
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

    AutoImportItem importItem = getAutoImportItem(file, "SomeClass*");
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

    AutoImportItem importItem = getAutoImportItem(file, "SomeClass*");
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

    AutoImportItem importItem = getAutoImportItem(newFile, "SomeClass*");
    assertThat(importItem.getTitleAndLocation().getFirst()).isEqualTo("class 'SomeClass'");
    assertThat(importItem.getTitleAndLocation().getSecond()).isEqualTo("\"foo/bar/test.proto.h\"");
  }

  private AutoImportItem getAutoImportItem(OCFile file, String referenceText) {
    testFixture.openFileInEditor(file.getVirtualFile());
    OCReferenceElement referenceElement =
        testFixture.findElementByText(referenceText, OCReferenceElement.class);
    OCImportSymbolFix fix = new OCImportSymbolFix(referenceElement);
    return Iterables.getOnlyElement(fix.getAutoImportItems());
  }
}
