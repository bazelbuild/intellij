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
package com.google.idea.blaze.android.sync.model.idea;

import static com.google.common.truth.Truth.assertThat;

import com.google.idea.blaze.android.AndroidIntegrationTestSetupRule;
import com.google.idea.blaze.android.projectsystem.PsiBasedClassFileFinder;
import com.google.idea.blaze.base.BlazeIntegrationTestCase;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.ideinfo.JavaIdeInfo;
import com.google.idea.blaze.base.ideinfo.LibraryArtifact;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetMap;
import com.google.idea.blaze.base.ideinfo.TargetMapBuilder;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.MockBlazeProjectDataBuilder;
import com.google.idea.blaze.base.model.MockBlazeProjectDataManager;
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.sync.SyncCache;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.base.sync.workspace.ArtifactLocationDecoder;
import com.intellij.openapi.vfs.VirtualFile;
import java.io.File;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Integration tests for {@link PsiBasedClassFileFinder}. */
@RunWith(JUnit4.class)
public class PsiBasedClassFileFinderTest extends BlazeIntegrationTestCase {
  @Rule
  public final AndroidIntegrationTestSetupRule androidSetupRule =
      new AndroidIntegrationTestSetupRule();

  private static final String BLAZE_BIN = "blaze-out/crosstool/bin";

  private PsiBasedClassFileFinder classFileFinder;

  @Before
  public void doSetUp() {
    classFileFinder = new PsiBasedClassFileFinder(testFixture.getModule());
  }

  private ArtifactLocation createSourceFile(String path, String contents) {
    fileSystem.createFile(path, contents);

    return ArtifactLocation.builder().setIsSource(true).setRelativePath(path).build();
  }

  private LibraryArtifact.Builder createClassJar(
      String rootExecutionPathFragment, String jarPath, String... contentPaths) {
    fileSystem.createFile(rootExecutionPathFragment + "/" + jarPath);
    for (String path : contentPaths) {
      fileSystem.createFile(rootExecutionPathFragment + "/" + jarPath + "!/" + path);
    }

    return LibraryArtifact.builder()
        .setClassJar(
            ArtifactLocation.builder()
                .setRootExecutionPathFragment(rootExecutionPathFragment)
                .setRelativePath(jarPath)
                .setIsSource(false)
                .build());
  }

  private void setTargetMap(TargetMap targetMap) {
    ArtifactLocationDecoder decoder =
        (location) -> new File("/src", location.getExecutionRootRelativePath());

    BlazeProjectData blazeProjectData =
        MockBlazeProjectDataBuilder.builder(workspaceRoot)
            .setTargetMap(targetMap)
            .setArtifactLocationDecoder(decoder)
            .build();

    registerProjectService(
        BlazeProjectDataManager.class, new MockBlazeProjectDataManager(blazeProjectData));

    // SourceToTargetMap stores its backing map in the SyncCache.
    // It's no longer valid since we've changed the target map.
    SyncCache.getInstance(testFixture.getProject()).clear();
  }

  @Test
  public void findClassFile_fromSourceInModule() {
    ArtifactLocation mainSource =
        createSourceFile("p1/p2/Main.java", "package p1.p2; class Main {}");
    LibraryArtifact.Builder classJar =
        createClassJar(BLAZE_BIN, "p1/p2/libmain.jar", "p1/p2/Main.class");

    setTargetMap(
        TargetMapBuilder.builder()
            .addTarget(
                TargetIdeInfo.builder()
                    .setLabel(Label.create("//p1/p2:main"))
                    .setKind(Kind.JAVA_BINARY)
                    .setJavaInfo(JavaIdeInfo.builder().addJar(classJar))
                    .addSource(mainSource))
            .build());

    VirtualFile classFile = classFileFinder.findClassFile("p1.p2.Main");
    assertThat(classFile).isNotNull();
    assertThat(classFile)
        .isEqualTo(fileSystem.findFile(BLAZE_BIN + "/p1/p2/libmain.jar!/p1/p2/Main.class"));
  }

  @Test
  public void findClassFile_fromSourceInModuleForInnerClass() {
    String sourceContents = "package nested; class Outer { class Middle { class Inner {} } }";
    ArtifactLocation nestedSource = createSourceFile("nested/Outer.java", sourceContents);

    String[] classFiles =
        new String[] {
          "nested/Outer.class", "nested/Outer$Middle.class", "nested/Outer$Middle$Inner.class"
        };
    LibraryArtifact.Builder classJar = createClassJar(BLAZE_BIN, "nested/libmain.jar", classFiles);

    setTargetMap(
        TargetMapBuilder.builder()
            .addTarget(
                TargetIdeInfo.builder()
                    .setLabel(Label.create("//nested:nested"))
                    .setKind(Kind.JAVA_BINARY)
                    .setJavaInfo(JavaIdeInfo.builder().addJar(classJar))
                    .addSource(nestedSource))
            .build());

    VirtualFile classFile = classFileFinder.findClassFile("nested.Outer$Middle$Inner");
    assertThat(classFile).isNotNull();
    assertThat(classFile)
        .isEqualTo(
            fileSystem.findFile(
                BLAZE_BIN + "/nested/libmain.jar!/nested/Outer$Middle$Inner.class"));
  }

  @Test
  public void findClassFile_fromMultiplePsiClasses() {
    String sourceContentTemplate =
        "package versioned; class Versioned { final static int VERSION = %d; }";

    // Here we create two versions of the "Versioned" class in two different source files.
    // This means JavaPsiFacade will identify two PsiClass instances corresponding to
    // versioned.Versioned
    createSourceFile("versioned/NotSelected.java", String.format(sourceContentTemplate, 0));
    ArtifactLocation selectedSource =
        createSourceFile("versioned/Selected.java", String.format(sourceContentTemplate, 1));

    LibraryArtifact.Builder classJar =
        createClassJar(BLAZE_BIN, "versioned/libversioned.jar", "versioned/Versioned.class");

    // We simulate a call to select() in //versioned:versioned's srcs attribute by including one
    // source file in the target map but not the other.
    setTargetMap(
        TargetMapBuilder.builder()
            .addTarget(
                TargetIdeInfo.builder()
                    .setLabel(Label.create("//versioned:versioned"))
                    .setKind(Kind.JAVA_BINARY)
                    .setJavaInfo(JavaIdeInfo.builder().addJar(classJar))
                    .addSource(selectedSource))
            .build());

    // The class file finder should choose the source file that's actually included in Blaze's model
    // of the project and return the corresponding class file.
    VirtualFile classFile = classFileFinder.findClassFile("versioned.Versioned");
    assertThat(classFile).isNotNull();
    assertThat(classFile)
        .isEqualTo(
            fileSystem.findFile(
                BLAZE_BIN + "/versioned/libversioned.jar!/versioned/Versioned.class"));
  }
}
