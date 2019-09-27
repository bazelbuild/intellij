/*
 * Copyright 2019 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.dependencies;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.base.BlazeIntegrationTestCase;
import com.google.idea.blaze.base.WorkspaceFileSystem;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetMap;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.MockBlazeProjectDataBuilder;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.testing.ServiceHelper;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiImportStatement;
import com.intellij.psi.PsiRecursiveElementWalkingVisitor;
import com.intellij.psi.PsiReference;
import java.util.Arrays;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/** Tests for {@link MissingDependencyTargetProviderImpl}. */
@RunWith(JUnit4.class)
public class MissingDependencyTargetProviderImplTest extends BlazeIntegrationTestCase {

  private static final String ANIMAL_SOURCE_FILE_RELATIVE_PATH = "java/com/google/test/Animal.java";
  private static final String[] ANIMAL_SOURCE_FILE_CONTENTS =
      new String[] {
        "package com.google.test;",
        "",
        "import com.google.test.pet.Cat;",
        "import com.google.test.pet.Dog;",
        "import com.google.test.pet.Pig;",
        "",
        "public class Animal {",
        "  public void doSomething() {",
        "    Dog dog = new Dog();",
        "    Pig pig = new Pig();",
        "    Cat cat = new Cat();",
        "  }",
        "}"
      };
  private static final String CAT_SOURCE_FILE_RELATIVE_PATH = "java/com/google/test/pet/Cat.java";
  private static final String[] CAT_SOURCE_FILE_CONTENTS =
      new String[] {"package com.google.test.pet;", "", "public class Cat {}"};
  private static final String DOG_SOURCE_FILE_RELATIVE_PATH = "java/com/google/test/pet/Dog.java";
  private static final String[] DOG_SOURCE_FILE_CONTENTS =
      new String[] {"package com.google.test.pet;", "", "public class Dog {}"};
  private static final String PIG_SOURCE_FILE_RELATIVE_PATH = "java/com/google/test/pet/Pig.java";
  private static final String[] PIG_SOURCE_FILE_CONTENTS =
      new String[] {"package com.google.test.pet;", "", "public class Pig {}"};
  private static final ImmutableMap<String, String[]> SOURCE_FILE_RELATIVE_PATH_TO_CONTENTS =
      ImmutableMap.<String, String[]>builder()
          .put(ANIMAL_SOURCE_FILE_RELATIVE_PATH, ANIMAL_SOURCE_FILE_CONTENTS)
          .put(CAT_SOURCE_FILE_RELATIVE_PATH, CAT_SOURCE_FILE_CONTENTS)
          .put(DOG_SOURCE_FILE_RELATIVE_PATH, DOG_SOURCE_FILE_CONTENTS)
          .put(PIG_SOURCE_FILE_RELATIVE_PATH, PIG_SOURCE_FILE_CONTENTS)
          .build();

  @Mock private BlazeProjectDataManager mockProjectDataManager;

  private MissingDependencyTargetProviderImpl underTest;

  @Before
  public void initTest() {
    MockitoAnnotations.initMocks(this);
    ServiceHelper.registerProjectService(
        testFixture.getProject(),
        BlazeProjectDataManager.class,
        mockProjectDataManager,
        getTestRootDisposable());
    underTest = new MissingDependencyTargetProviderImpl();
  }

  @Test
  public void getMissingDependencyTargets_singleTargetBuildingSourceFile() {
    ImmutableMap<String, PsiFile> sourceFiles = initializeSourceFiles(workspace);
    ImmutableMap<String, ArtifactLocation> sourceLocations = createSourceLocations();
    ImmutableList<TargetIdeInfo> ideInfos =
        ImmutableList.of(
            createTargetIdeInfo(
                "//test:animal",
                sourceLocations.get(ANIMAL_SOURCE_FILE_RELATIVE_PATH),
                "//test:cat",
                "//test:pig"),
            createTargetIdeInfo("//test:cat", sourceLocations.get(CAT_SOURCE_FILE_RELATIVE_PATH)),
            createTargetIdeInfo(
                "//test:dog", sourceLocations.get(DOG_SOURCE_FILE_RELATIVE_PATH), "//test:pig"));
    BlazeProjectData projectData =
        MockBlazeProjectDataBuilder.builder(workspaceRoot)
            .setTargetMap(
                new TargetMap(
                    ideInfos.stream()
                        .collect(
                            ImmutableMap.toImmutableMap(
                                TargetIdeInfo::getKey, ideInfo -> ideInfo))))
            .build();
    PsiFile mainSourceFile = sourceFiles.get(ANIMAL_SOURCE_FILE_RELATIVE_PATH);
    ImmutableSet<PsiReference> references = getImportReferences(mainSourceFile);

    when(mockProjectDataManager.getBlazeProjectData()).thenReturn(projectData);

    ImmutableList<MissingDependencyData> actual =
        underTest.getMissingDependencyTargets(mainSourceFile, references);

    PsiReference expectedReference = getReference(references, "Dog");
    assertThat(actual)
        .containsExactly(
            MissingDependencyData.builder()
                .setReference(expectedReference)
                .setDependencyTargets(
                    ImmutableListMultimap.of(
                        Label.create("//test:animal"), Label.create("//test:dog")))
                .build());
  }

  @Test
  public void getMissingDependencyTargets_multipleTargetsBuildingSourceFile() {
    ImmutableMap<String, PsiFile> sourceFiles = initializeSourceFiles(workspace);
    ImmutableMap<String, ArtifactLocation> sourceLocations = createSourceLocations();
    ImmutableList<TargetIdeInfo> ideInfos =
        ImmutableList.of(
            createTargetIdeInfo(
                "//test1:animal",
                sourceLocations.get(ANIMAL_SOURCE_FILE_RELATIVE_PATH),
                "//test2:cat",
                "//test1:pig"),
            createTargetIdeInfo(
                "//test2:animal",
                sourceLocations.get(ANIMAL_SOURCE_FILE_RELATIVE_PATH),
                "//test1:cat"),
            createTargetIdeInfo("//test1:cat", sourceLocations.get(CAT_SOURCE_FILE_RELATIVE_PATH)),
            createTargetIdeInfo("//test2:cat", sourceLocations.get(CAT_SOURCE_FILE_RELATIVE_PATH)),
            createTargetIdeInfo("//test1:dog", sourceLocations.get(DOG_SOURCE_FILE_RELATIVE_PATH)),
            createTargetIdeInfo("//test2:dog", sourceLocations.get(DOG_SOURCE_FILE_RELATIVE_PATH)),
            createTargetIdeInfo("//test1:pig", sourceLocations.get(PIG_SOURCE_FILE_RELATIVE_PATH)),
            createTargetIdeInfo("//test2:pig", sourceLocations.get(PIG_SOURCE_FILE_RELATIVE_PATH)));
    BlazeProjectData projectData =
        MockBlazeProjectDataBuilder.builder(workspaceRoot)
            .setTargetMap(
                new TargetMap(
                    ideInfos.stream()
                        .collect(
                            ImmutableMap.toImmutableMap(
                                TargetIdeInfo::getKey, ideInfo -> ideInfo))))
            .build();
    PsiFile mainSourceFile = sourceFiles.get(ANIMAL_SOURCE_FILE_RELATIVE_PATH);
    ImmutableSet<PsiReference> references = getImportReferences(mainSourceFile);

    when(mockProjectDataManager.getBlazeProjectData()).thenReturn(projectData);

    ImmutableList<MissingDependencyData> actual =
        underTest.getMissingDependencyTargets(mainSourceFile, references);

    PsiReference expectedDogReference = getReference(references, "Dog");
    PsiReference expectedPigReference = getReference(references, "Pig");
    assertThat(actual)
        .containsExactly(
            MissingDependencyData.builder()
                .setReference(expectedDogReference)
                .setDependencyTargets(
                    ImmutableListMultimap.<Label, Label>builder()
                        .putAll(
                            Label.create("//test1:animal"),
                            Label.create("//test1:dog"),
                            Label.create("//test2:dog"))
                        .putAll(
                            Label.create("//test2:animal"),
                            Label.create("//test1:dog"),
                            Label.create("//test2:dog"))
                        .build())
                .build(),
            MissingDependencyData.builder()
                .setReference(expectedPigReference)
                .setDependencyTargets(
                    ImmutableListMultimap.<Label, Label>builder()
                        .putAll(
                            Label.create("//test2:animal"),
                            Label.create("//test1:pig"),
                            Label.create("//test2:pig"))
                        .build())
                .build());
  }

  private static PsiReference getReference(ImmutableSet<PsiReference> references, String text) {
    return references.stream()
        .filter(reference -> reference.getCanonicalText().contains(text))
        .findFirst()
        .get();
  }

  private static ImmutableSet<PsiReference> getImportReferences(PsiFile file) {
    ImmutableSet.Builder<PsiReference> references = ImmutableSet.builder();
    file.accept(
        new PsiRecursiveElementWalkingVisitor() {
          @Override
          public void visitElement(PsiElement element) {
            if (element.getParent() instanceof PsiImportStatement) {
              references.add(element.getReferences());
            }
            super.visitElement(element);
          }
        });
    return references.build();
  }

  private static ImmutableMap<String, PsiFile> initializeSourceFiles(
      WorkspaceFileSystem workspace) {
    return SOURCE_FILE_RELATIVE_PATH_TO_CONTENTS.entrySet().stream()
        .collect(
            ImmutableMap.toImmutableMap(
                Map.Entry::getKey,
                entry -> createSourceFile(workspace, entry.getKey(), entry.getValue())));
  }

  private static PsiFile createSourceFile(
      WorkspaceFileSystem workspace, String relativePath, String... contents) {
    return workspace.createPsiFile(new WorkspacePath(relativePath), contents);
  }

  private static ImmutableMap<String, ArtifactLocation> createSourceLocations() {
    return SOURCE_FILE_RELATIVE_PATH_TO_CONTENTS.keySet().stream()
        .collect(
            ImmutableMap.toImmutableMap(
                relativePath -> relativePath,
                relativePath ->
                    ArtifactLocation.builder()
                        .setIsExternal(false)
                        .setIsSource(true)
                        .setRelativePath(relativePath)
                        .build()));
  }

  private static TargetIdeInfo createTargetIdeInfo(
      String label, ArtifactLocation sourceLocation, String... dependencies) {
    // We use "proto_library" here to avoid packaging Java-specific rule classes in the test.
    TargetIdeInfo.Builder ideInfo =
        TargetIdeInfo.builder().setLabel(label).setKind("proto_library").addSource(sourceLocation);
    Arrays.stream(dependencies).forEach(dependency -> ideInfo.addDependency(dependency));
    return ideInfo.build();
  }
}
