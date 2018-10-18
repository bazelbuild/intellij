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
package com.google.idea.blaze.golang.run.smrunner;

import static com.google.common.truth.Truth.assertThat;

import com.goide.psi.GoFile;
import com.goide.psi.GoFunctionDeclaration;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.base.BlazeIntegrationTestCase;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.ideinfo.GoIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetMap;
import com.google.idea.blaze.base.ideinfo.TargetMapBuilder;
import com.google.idea.blaze.base.lang.buildfile.psi.BuildFile;
import com.google.idea.blaze.base.lang.buildfile.psi.FuncallExpression;
import com.google.idea.blaze.base.lang.buildfile.psi.util.PsiUtils;
import com.google.idea.blaze.base.model.MockBlazeProjectDataBuilder;
import com.google.idea.blaze.base.model.MockBlazeProjectDataManager;
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.model.primitives.WorkspaceType;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.base.sync.projectview.WorkspaceLanguageSettings;
import com.intellij.execution.Location;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.search.GlobalSearchScope;
import java.util.List;
import javax.annotation.Nullable;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Integration tests for {@link BlazeGoTestEventsHandler}. */
@RunWith(JUnit4.class)
public class BlazeGoTestEventsHandlerTest extends BlazeIntegrationTestCase {

  private final BlazeGoTestEventsHandler handler = new BlazeGoTestEventsHandler();

  @Test
  public void testSuiteLocationResolvesToBuildRule() {
    TargetMap targetMap =
        TargetMapBuilder.builder()
            .addTarget(
                TargetIdeInfo.builder()
                    .setLabel("//foo/bar:foo_test")
                    .setKind("go_test")
                    .setBuildFile(src("foo/bar/BUILD"))
                    .addSource(src("foo/bar/foo_test.go"))
                    .addSource(src("foo/bar/bar_test.go"))
                    .setGoInfo(
                        GoIdeInfo.builder()
                            .setLabel(Label.create("//foo/bar:foo_test"))
                            .setKind(Kind.GO_TEST)
                            .addSources(ImmutableList.of(src("foo/bar/foo_test.go")))
                            .addSources(ImmutableList.of(src("foo/bar/bar_test.go")))
                            .setImportPath("google3/foo/bar/foo")))
            .build();

    registerProjectService(
        BlazeProjectDataManager.class,
        new MockBlazeProjectDataManager(
            MockBlazeProjectDataBuilder.builder(workspaceRoot)
                .setTargetMap(targetMap)
                .setWorkspaceLanguageSettings(
                    new WorkspaceLanguageSettings(
                        WorkspaceType.GO, ImmutableSet.of(LanguageClass.GO)))
                .build()));

    workspace.createFile(
        new WorkspacePath("foo/bar/foo_test.go"),
        "package foo",
        "import \"testing\"",
        "func TestFoo(t *testing.T) {}");

    workspace.createFile(
        new WorkspacePath("foo/bar/bar_test.go"),
        "package foo",
        "import \"testing\"",
        "func TestBar(t *testing.T) {}");

    BuildFile buildFile =
        (BuildFile)
            workspace.createPsiFile(
                new WorkspacePath("foo/bar/BUILD"),
                "go_test(",
                "    name = 'foo_test',",
                "    srcs = [",
                "        'foo_test.go',",
                "        'bar_test.go',",
                "    ],",
                ")");

    FuncallExpression buildRule =
        PsiUtils.findFirstChildOfClassRecursive(buildFile, FuncallExpression.class);
    assertThat(buildRule).isNotNull();

    String url = handler.suiteLocationUrl(null, "foo/bar/foo_test");
    Location<?> location = getLocation(url);
    assertThat(location).isNotNull();
    assertThat(location.getPsiElement()).isEqualTo(buildRule);
  }

  @Test
  public void testSuiteLocationResolvesToSingleSourceFile() {
    TargetMap targetMap =
        TargetMapBuilder.builder()
            .addTarget(
                TargetIdeInfo.builder()
                    .setLabel("//foo/bar:foo_test")
                    .setKind("go_test")
                    .setBuildFile(src("foo/bar/BUILD"))
                    .addSource(src("foo/bar/foo_test.go"))
                    .setGoInfo(
                        GoIdeInfo.builder()
                            .setLabel(Label.create("//foo/bar:foo_test"))
                            .setKind(Kind.GO_TEST)
                            .addSources(ImmutableList.of(src("foo/bar/foo_test.go")))
                            .setImportPath("google3/foo/bar/foo")))
            .build();

    registerProjectService(
        BlazeProjectDataManager.class,
        new MockBlazeProjectDataManager(
            MockBlazeProjectDataBuilder.builder(workspaceRoot)
                .setTargetMap(targetMap)
                .setWorkspaceLanguageSettings(
                    new WorkspaceLanguageSettings(
                        WorkspaceType.GO, ImmutableSet.of(LanguageClass.GO)))
                .build()));

    GoFile goFile =
        (GoFile)
            workspace.createPsiFile(
                new WorkspacePath("foo/bar/foo_test.go"),
                "package foo",
                "import \"testing\"",
                "func TestFoo(t *testing.T) {}");

    workspace.createFile(
        new WorkspacePath("foo/bar/BUILD"),
        "go_test(",
        "    name = 'foo_test',",
        "    srcs = ['foo_test.go'],",
        ")");

    String url = handler.suiteLocationUrl(null, "foo/bar/foo_test");
    Location<?> location = getLocation(url);
    assertThat(location).isNotNull();
    assertThat(location.getPsiElement()).isEqualTo(goFile);
  }

  @Test
  public void testTopLevelTestTargetResolves() {
    TargetMap targetMap =
        TargetMapBuilder.builder()
            .addTarget(
                TargetIdeInfo.builder()
                    .setLabel("//:foo_test")
                    .setKind("go_test")
                    .setBuildFile(src("BUILD"))
                    .addSource(src("foo_test.go"))
                    .setGoInfo(
                        GoIdeInfo.builder()
                            .setLabel(Label.create("//:foo_test"))
                            .setKind(Kind.GO_TEST)
                            .addSources(ImmutableList.of(src("foo_test.go")))
                            .setImportPath("foo")))
            .build();

    registerProjectService(
        BlazeProjectDataManager.class,
        new MockBlazeProjectDataManager(
            MockBlazeProjectDataBuilder.builder(workspaceRoot)
                .setTargetMap(targetMap)
                .setWorkspaceLanguageSettings(
                    new WorkspaceLanguageSettings(
                        WorkspaceType.GO, ImmutableSet.of(LanguageClass.GO)))
                .build()));

    GoFile goFile =
        (GoFile)
            workspace.createPsiFile(
                new WorkspacePath("foo_test.go"),
                "package foo",
                "import \"testing\"",
                "func TestFoo(t *testing.T) {}");

    workspace.createFile(
        new WorkspacePath("BUILD"),
        "go_test(",
        "    name = 'foo_test',",
        "    srcs = ['foo_test.go'],",
        ")");

    String url = handler.suiteLocationUrl(null, "foo_test");
    Location<?> location = getLocation(url);
    assertThat(location).isNotNull();
    assertThat(location.getPsiElement()).isEqualTo(goFile);
  }

  @Test
  public void testFunctionLocationResolves() {
    TargetMap targetMap =
        TargetMapBuilder.builder()
            .addTarget(
                TargetIdeInfo.builder()
                    .setLabel("//foo/bar:foo_test")
                    .setKind("go_test")
                    .setBuildFile(src("foo/bar/BUILD"))
                    .addSource(src("foo/bar/foo_test.go"))
                    .setGoInfo(
                        GoIdeInfo.builder()
                            .setLabel(Label.create("//foo/bar:foo_test"))
                            .setKind(Kind.GO_TEST)
                            .addSources(ImmutableList.of(src("foo/bar/foo_test.go")))
                            .setImportPath("google3/foo/bar/foo")))
            .build();

    registerProjectService(
        BlazeProjectDataManager.class,
        new MockBlazeProjectDataManager(
            MockBlazeProjectDataBuilder.builder(workspaceRoot)
                .setTargetMap(targetMap)
                .setWorkspaceLanguageSettings(
                    new WorkspaceLanguageSettings(
                        WorkspaceType.GO, ImmutableSet.of(LanguageClass.GO)))
                .build()));
    GoFile goFile =
        (GoFile)
            workspace.createPsiFile(
                new WorkspacePath("foo/bar/foo_test.go"),
                "package foo",
                "import \"testing\"",
                "func TestFoo(t *testing.T) {}");

    workspace.createFile(
        new WorkspacePath("foo/bar/BUILD"),
        "go_test(",
        "    name = 'foo_test',",
        "    srcs = ['foo_test.go'],",
        ")");

    GoFunctionDeclaration function =
        PsiUtils.findFirstChildOfClassRecursive(goFile, GoFunctionDeclaration.class);
    assertThat(function).isNotNull();

    String url = handler.testLocationUrl(null, "foo/bar/foo_test", "TestFoo", null);
    Location<?> location = getLocation(url);
    assertThat(location).isNotNull();
    assertThat(location.getPsiElement()).isEqualTo(function);
  }

  @Nullable
  private Location<?> getLocation(String url) {
    String protocol = VirtualFileManager.extractProtocol(url);
    if (protocol == null) {
      return null;
    }
    String path = VirtualFileManager.extractPath(url);
    assertThat(handler.getTestLocator()).isNotNull();
    @SuppressWarnings("rawtypes")
    List<Location> locations =
        handler
            .getTestLocator()
            .getLocation(protocol, path, getProject(), GlobalSearchScope.allScope(getProject()));
    assertThat(locations).hasSize(1);
    return locations.get(0);
  }

  private static ArtifactLocation src(String relativePath) {
    return ArtifactLocation.builder().setRelativePath(relativePath).setIsSource(true).build();
  }
}
