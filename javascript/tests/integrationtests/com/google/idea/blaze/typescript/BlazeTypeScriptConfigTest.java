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
package com.google.idea.blaze.typescript;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.idea.blaze.base.BlazeIntegrationTestCase;
import com.google.idea.blaze.base.MockProjectViewManager;
import com.google.idea.blaze.base.TestFileSystem.MockFileOperationProvider;
import com.google.idea.blaze.base.io.FileOperationProvider;
import com.google.idea.blaze.base.io.VfsUtils;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.MockBlazeProjectDataBuilder;
import com.google.idea.blaze.base.model.MockBlazeProjectDataManager;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.projectview.ProjectView;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.projectview.section.ListSection;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.sdkcompat.typescript.TypeScriptConfigCompat;
import com.google.idea.sdkcompat.typescript.TypeScriptConfigServiceCompat;
import com.intellij.lang.javascript.frameworks.modules.JSModulePathSubstitution;
import com.intellij.lang.typescript.library.TypeScriptLibraryProvider;
import com.intellij.lang.typescript.tsconfig.TypeScriptConfig;
import com.intellij.lang.typescript.tsconfig.TypeScriptConfigServiceImpl;
import com.intellij.lang.typescript.tsconfig.graph.TypeScriptConfigGraphCache;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiManager;
import java.io.File;
import java.io.IOException;
import java.util.Comparator;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link BlazeTypeScriptConfig} */
@RunWith(JUnit4.class)
public class BlazeTypeScriptConfigTest extends BlazeIntegrationTestCase {
  // disposed prior to calling parent class's @After methods
  private final Disposable thisClassDisposable = Disposer.newDisposable();

  private BlazeTypeScriptConfigServiceImpl blazeConfigService;
  private TypeScriptConfigServiceImpl regularConfigService;

  @Before
  public final void before() {
    fileSystem.createFile(
        "/src/workspace/project/foo/tsconfig.json",
        "{\"extends\": \"../../blaze-bin/project/foo/tsconfig_editor.json\"}");
    String[] tsconfigEditorContents =
        new String[] {
          "{",
          "    \"compileOnSave\": false,",
          "    \"compilerOptions\": {",
          "        \"baseUrl\": \".\",",
          "        \"declaration\": false,",
          "        \"downlevelIteration\": true,",
          "        \"emitDecoratorMetadata\": true,",
          "        \"experimentalDecorators\": true,",
          "        \"importHelpers\": true,",
          "        \"inlineSourceMap\": true,",
          "        \"inlineSources\": true,",
          "        \"jsx\": \"react\",",
          "        \"jsxFactory\": \"React.createElement\",",
          "        \"module\": \"commonjs\",",
          "        \"moduleResolution\": \"node\",",
          "        \"noEmit\": true,",
          "        \"noEmitOnError\": false,",
          "        \"noErrorTruncation\": false,",
          "        \"noFallthroughCasesInSwitch\": true,",
          "        \"noImplicitAny\": true,",
          "        \"noImplicitReturns\": true,",
          "        \"noImplicitThis\": true,",
          "        \"noLib\": true,",
          "        \"noResolve\": false,",
          "        \"paths\": {",
          "            \"workspace/*\": [",
          "                \"./tsconfig.runfiles/workspace/*\"",
          "            ],",
          "            \"workspace/project/foo/*\": [",
          "                \"../../../project/foo/*\",",
          "                \"./tsconfig.runfiles/workspace/project/foo/*\"",
          "            ]",
          "        },",
          "        \"plugins\": [",
          "            {",
          "                \"disabledRules\": [],",
          "                \"name\": \"@bazel/tsetse\"",
          "            },",
          "            {",
          "                \"name\": \"ide_performance\"",
          "            }",
          "        ],",
          "        \"preserveConstEnums\": false,",
          "        \"rootDirs\": [",
          "            \"../../../project/foo\",",
          "            \"./tsconfig.runfiles/workspace/project/foo\",",
          "            \"./tsconfig.runfiles/workspace\"",
          "        ],",
          "        \"skipDefaultLibCheck\": true,",
          "        \"sourceMap\": false,",
          "        \"strictFunctionTypes\": true,",
          "        \"strictNullChecks\": true,",
          "        \"strictPropertyInitialization\": true,",
          "        \"stripInternal\": true,",
          "        \"target\": \"es5\",",
          "        \"types\": []",
          "    },",
          "    \"files\": [",
          "        \"./tsconfig.runfiles/workspace/javascript/closure/base.d.ts\",",
          "        \"../../../project/foo/included.ts\"",
          "    ]",
          "}"
        };
    fileSystem.createFile(
        "/src/out/execroot/bin/project/foo/tsconfig_editor.json", tsconfigEditorContents);
    fileSystem.createFile(
        "/src/workspace/blaze-bin/project/foo/tsconfig_editor.json", tsconfigEditorContents);
    fileSystem.createFile("/src/workspace/project/foo/included.ts");
    fileSystem.createFile("/src/workspace/project/foo/excluded.ts");
    fileSystem.createFile(
        "/src/out/execroot/bin/project/foo/tsconfig.runfiles/workspace/javascript/closure/base.d.ts");
    fileSystem.createFile(
        "/src/workspace/blaze-bin/project/foo/tsconfig.runfiles/workspace/javascript/closure/base.d.ts");
    fileSystem.createDirectory(
        "/src/out/execroot/bin/project/foo/tsconfig.runfiles/workspace/project/foo");
    fileSystem.createDirectory(
        "/src/workspace/blaze-bin/project/foo/tsconfig.runfiles/workspace/project/foo");

    registerApplicationService(
        FileOperationProvider.class,
        new MockFileOperationProviderWithSymlinks(
            ImmutableMap.of(
                "/src/workspace/blaze-bin", "/src/out/execroot/bin",
                "/src/workspace/blaze-genfiles", "/src/out/execroot/genfiles")));

    MockProjectViewManager projectViewManager =
        new MockProjectViewManager(getProject(), thisClassDisposable);
    projectViewManager.setProjectView(
        new ProjectViewSet.Builder()
            .add(
                ProjectView.builder()
                    .add(
                        ListSection.builder(TsConfigRulesSection.KEY)
                            .add(Label.create("//project/foo:tsconfig")))
                    .build())
            .build());
    BlazeProjectData projectData =
        MockBlazeProjectDataBuilder.builder()
            .setOutputBase(fileSystem.getRootDir() + "/out")
            .build();
    registerProjectService(
        BlazeProjectDataManager.class, new MockBlazeProjectDataManager(projectData));

    this.blazeConfigService = new BlazeTypeScriptConfigServiceImpl(getProject());
    blazeConfigService.update(projectData);
    this.regularConfigService =
        TypeScriptConfigServiceCompat.newImpl(
            getProject(),
            null,
            PsiManager.getInstance(getProject()),
            TypeScriptLibraryProvider.getService(getProject()),
            new TypeScriptConfigGraphCache(getProject()));
  }

  @Test
  public void testCompileOnsave() {
    TypeScriptConfigCompat blazeConfig =
        (TypeScriptConfigCompat) blazeConfigService.getConfigs().get(0);
    // regularConfig incorrectly parses this as true, since it uses the value from the empty
    // extender config instead of the extendee, and the default value is true
    assertThat(blazeConfig.isCompileOnSave()).isFalse();
  }

  @Test
  public void testSameOptions() {
    assertThat(blazeConfigService.getConfigs()).hasSize(1);
    assertThat(regularConfigService.getConfigFiles()).hasSize(1);
    TypeScriptConfigCompat blazeConfig =
        (TypeScriptConfigCompat) blazeConfigService.getConfigs().get(0);
    TypeScriptConfig regularConfig =
        TypeScriptConfigServiceCompat.getConfigs(regularConfigService).get(0);

    assertThat(blazeConfig.isDirectoryBased()).isEqualTo(regularConfig.isDirectoryBased());
    assertThat(blazeConfig.getConfigFile()).isEqualTo(regularConfig.getConfigFile());
    assertThat(blazeConfig.getConfigDirectory()).isEqualTo(regularConfig.getConfigDirectory());
    assertThat(blazeConfig.getLanguageTarget()).isEqualTo(regularConfig.getLanguageTarget());
    assertThat(blazeConfig.getOutDirectory()).isEqualTo(regularConfig.getOutDirectory());
    assertThat(blazeConfig.hasErrors()).isEqualTo(regularConfig.hasErrors());
    assertThat(blazeConfig.isInlineSourceMap()).isEqualTo(regularConfig.isInlineSourceMap());
    assertThat(blazeConfig.isSourceMap()).isEqualTo(regularConfig.isSourceMap());
    assertThat(blazeConfig.getLibNames()).isEmpty();
    assertThat(blazeConfig.getLibNames()).containsExactlyElementsIn(regularConfig.getLibNames());
    assertThat(blazeConfig.getTypeRoots()).isEmpty();
    assertThat(blazeConfig.getTypeRoots()).containsExactlyElementsIn(regularConfig.getTypeRoots());
    assertThat(blazeConfig.getResolution()).isEqualTo(regularConfig.getResolution());
    assertThat(blazeConfig.getEffectiveResolution())
        .isEqualTo(regularConfig.getEffectiveResolution());
    assertThat(blazeConfig.getTypes()).isEmpty();
    assertThat(blazeConfig.getTypes()).containsExactlyElementsIn(regularConfig.getTypes());
    assertThat(blazeConfig.getModule()).isEqualTo(regularConfig.getModule());
    assertThat(blazeConfig.hasExplicitCompileOnSave())
        .isEqualTo(regularConfig.hasExplicitCompileOnSave());
    assertThat(blazeConfig.getIncludePatterns())
        .containsExactlyElementsIn(regularConfig.getIncludePatterns());
    assertThat(blazeConfig.getExcludePatterns())
        .containsExactlyElementsIn(regularConfig.getExcludePatterns());
    assertThat(blazeConfig.hasIncludesList()).isEqualTo(regularConfig.hasIncludesList());
    assertThat(blazeConfig.allowJs()).isEqualTo(regularConfig.allowJs());
    assertThat(blazeConfig.suppressExcessPropertyChecks())
        .isEqualTo(regularConfig.suppressExcessPropertyChecks());
    assertThat(blazeConfig.checkJs()).isEqualTo(regularConfig.checkJs());
    assertThat(blazeConfig.noImplicitAny()).isEqualTo(regularConfig.noImplicitAny());
    assertThat(blazeConfig.noImplicitThis()).isEqualTo(regularConfig.noImplicitThis());
    assertThat(blazeConfig.strictNullChecks()).isEqualTo(regularConfig.strictNullChecks());
    assertThat(blazeConfig.strictBindCallApply()).isEqualTo(regularConfig.strictBindCallApply());
    assertThat(blazeConfig.allowSyntheticDefaultImports())
        .isEqualTo(regularConfig.allowSyntheticDefaultImports());
    assertThat(blazeConfig.noLib()).isEqualTo(regularConfig.noLib());
    assertThat(blazeConfig.getRootDirFile()).isNull();
    assertThat(blazeConfig.getRootDirFile()).isEqualTo(regularConfig.getRootDirFile());
    assertThat(blazeConfig.getProjectReferences()).isEmpty();
    assertThat(blazeConfig.preserveSymlinks()).isEqualTo(regularConfig.preserveSymlinks());
    assertThat(blazeConfig.jsxFactory()).isEqualTo(regularConfig.jsxFactory());
    assertThat(blazeConfig.getPlugins()).containsExactly("@bazel/tsetse", "ide_performance");
    assertThat(blazeConfig.keyofStringsOnly()).isFalse();
  }

  @Test
  public void testFileList() {
    // regularConfig can't correctly parse the file list in test mode,
    // so these values are manually checked

    TypeScriptConfigCompat blazeConfig =
        (TypeScriptConfigCompat) blazeConfigService.getConfigs().get(0);
    VirtualFile includedSource = vf("/src/workspace/project/foo/included.ts");
    VirtualFile excludedSource = vf("/src/workspace/project/foo/excluded.ts");
    assertThat(blazeConfig.accept(includedSource)).isTrue();
    assertThat(blazeConfig.accept(excludedSource)).isFalse();

    // we don't use TypeScriptConfigPatternInclude so this is always false
    assertThat(blazeConfig.isIncludedFile(includedSource)).isFalse();
    assertThat(blazeConfig.isIncludedFile(excludedSource)).isFalse();

    assertThat(blazeConfig.isFromFileList(includedSource)).isTrue();
    assertThat(blazeConfig.isFromFileList(excludedSource)).isFalse();

    assertThat(blazeConfig.getFileList())
        .containsExactly(
            vf("/src/workspace/project/foo/included.ts"),
            vf(
                "/src/out/execroot/bin/project/foo/tsconfig.runfiles/workspace/javascript/closure/base.d.ts"));
    assertThat(blazeConfig.hasFilesList()).isTrue();
  }

  @Test
  public void testDifferentOptions() {
    TypeScriptConfig blazeConfig = blazeConfigService.getConfigs().get(0);
    assertThat(blazeConfig.getBaseUrl()).isEqualTo(vf("/src/out/execroot/bin/project/foo"));
    assertThat(blazeConfig.getDependencies())
        .containsExactly(vf("/src/out/execroot/bin/project/foo/tsconfig_editor.json"));

    ImmutableList<JSModulePathSubstitution> paths =
        ImmutableList.sortedCopyOf(
            Comparator.comparing(JSModulePathSubstitution::getMappedName), blazeConfig.getPaths());
    assertThat(paths).hasSize(2);
    JSModulePathSubstitution projectPath = paths.get(0);
    assertThat(projectPath.getMappedName()).isEqualTo("workspace");
    assertThat(projectPath.getMappings())
        .containsExactly(
            "../../../../../workspace/*",
            "../../../../../workspace/blaze-bin/*",
            "../../../../../workspace/blaze-genfiles/*",
            "../../*",
            "../../../genfiles/*",
            "./tsconfig.runfiles/workspace/*")
        .inOrder();

    JSModulePathSubstitution projectFooPath = paths.get(1);
    assertThat(projectFooPath.getMappedName()).isEqualTo("workspace/project/foo");
    assertThat(projectFooPath.getMappings())
        .containsExactly(
            "../../../../../workspace/project/foo/*",
            "../../../../../workspace/blaze-bin/project/foo/*",
            "../../../../../workspace/blaze-genfiles/project/foo/*",
            "../../project/foo/*",
            "../../../genfiles/project/foo/*",
            "./tsconfig.runfiles/workspace/project/foo/*")
        .inOrder();

    assertThat(blazeConfig.getRootDirsFiles())
        .containsExactly(
            vf("/src/workspace/project/foo"),
            vf("/src/out/execroot/bin/project/foo/tsconfig.runfiles/workspace"),
            vf("/src/out/execroot/bin/project/foo/tsconfig.runfiles/workspace/project/foo"));
    assertThat(blazeConfig.getRootDirs())
        .containsExactly(
            psi("/src/workspace/project/foo"),
            psi("/src/out/execroot/bin/project/foo/tsconfig.runfiles/workspace"),
            psi("/src/out/execroot/bin/project/foo/tsconfig.runfiles/workspace/project/foo"));
  }

  private static VirtualFile vf(String path) {
    VirtualFile resolved = VfsUtils.resolveVirtualFile(new File(path));
    assertThat(resolved).isNotNull();
    return resolved;
  }

  private PsiDirectory psi(String path) {
    PsiManager psiManager = PsiManager.getInstance(getProject());
    PsiDirectory psiDirectory = psiManager.findDirectory(vf(path));
    assertThat(psiDirectory).isNotNull();
    return psiDirectory;
  }

  private static class MockFileOperationProviderWithSymlinks extends MockFileOperationProvider {
    private final ImmutableMap<File, File> symlinks;

    MockFileOperationProviderWithSymlinks(Map<String, String> symlinks) {
      this.symlinks =
          symlinks.entrySet().stream()
              .collect(
                  ImmutableMap.toImmutableMap(
                      e -> new File(e.getKey()), e -> new File(e.getValue())));
    }

    @Override
    public boolean isSymbolicLink(File file) {
      return symlinks.containsKey(file);
    }

    @Override
    public File readSymbolicLink(File link) throws IOException {
      if (!isSymbolicLink(link)) {
        throw new IOException();
      }
      return symlinks.get(link);
    }
  }
}
