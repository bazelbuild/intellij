/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.io.FileAttributeProvider;
import com.google.idea.blaze.base.io.InputStreamProvider;
import com.google.idea.blaze.base.lang.buildfile.search.FindUsages;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.settings.Blaze.BuildSystem;
import com.google.idea.blaze.base.settings.BlazeImportSettings;
import com.google.idea.blaze.base.settings.BlazeImportSettingsManager;
import com.google.idea.blaze.base.sync.BlazeSyncPlugin;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.base.sync.projectstructure.ModuleEditorProvider;
import com.google.idea.testing.EdtRule;
import com.google.idea.testing.IntellijTestSetupRule;
import com.google.idea.testing.ServiceHelper;
import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.ex.temp.TempFileSystem;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.refactoring.move.moveClassesOrPackages.MoveDirectoryWithClassesProcessor;
import com.intellij.testFramework.EditorTestUtil;
import com.intellij.testFramework.EditorTestUtil.CaretAndSelectionState;
import com.intellij.testFramework.EditorTestUtil.CaretInfo;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.LightPlatformTestCase;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.TempDirTestFixture;
import com.intellij.testFramework.fixtures.TestFixtureBuilder;
import com.intellij.testFramework.fixtures.impl.LightTempDirTestFixtureImpl;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.TestRule;

/** Base test class for blaze integration tests. {@link UsefulTestCase} */
public abstract class BlazeIntegrationTestCase {

  private static final LightProjectDescriptor projectDescriptor =
      LightCodeInsightFixtureTestCase.JAVA_8;

  @Rule public final IntellijTestSetupRule setupRule = new IntellijTestSetupRule();
  @Rule public final TestRule testRunWrapper = runTestsOnEdt() ? new EdtRule() : null;

  protected CodeInsightTestFixture testFixture;
  protected WorkspaceRoot workspaceRoot;

  @Before
  public final void setUp() throws Exception {
    IdeaTestFixtureFactory factory = IdeaTestFixtureFactory.getFixtureFactory();
    TestFixtureBuilder<IdeaProjectTestFixture> fixtureBuilder =
        factory.createLightFixtureBuilder(projectDescriptor);
    final IdeaProjectTestFixture fixture = fixtureBuilder.getFixture();
    testFixture = factory.createCodeInsightFixture(fixture, createTempDirFixture());
    testFixture.setUp();

    Runnable writeAction =
        () ->
            ApplicationManager.getApplication()
                .runWriteAction(
                    () -> ProjectJdkTable.getInstance().addJdk(IdeaTestUtil.getMockJdk18()));
    EdtTestUtil.runInEdtAndWait(writeAction);

    workspaceRoot = new WorkspaceRoot(new File(LightPlatformTestCase.getSourceRoot().getPath()));
    setBlazeImportSettings(
        new BlazeImportSettings(
            workspaceRoot.toString(),
            "test-project",
            workspaceRoot + "/project-data-dir",
            "location-hash",
            workspaceRoot + "/project-view-file",
            buildSystem()));

    registerApplicationService(FileAttributeProvider.class, new TempFileAttributeProvider());
    registerApplicationService(
        InputStreamProvider.class,
        file -> {
          VirtualFile vf = findFile(file.getPath());
          if (vf == null) {
            throw new FileNotFoundException();
          }
          return vf.getInputStream();
        });
  }

  protected Disposable getTestRootDisposable() {
    return setupRule.testRootDisposable;
  }

  /** Override to run tests with bazel specified as the project's build system. */
  protected BuildSystem buildSystem() {
    return BuildSystem.Blaze;
  }

  /** Override to run tests off the EDT. */
  protected boolean runTestsOnEdt() {
    return true;
  }

  @After
  public final void tearDown() throws Exception {
    testFixture.tearDown();
    testFixture = null;
  }

  protected void setBlazeImportSettings(BlazeImportSettings importSettings) {
    BlazeImportSettingsManager.getInstance(getProject()).setImportSettings(importSettings);
  }

  /** @return fixture to be used as temporary dir. */
  protected TempDirTestFixture createTempDirFixture() {
    return new LightTempDirTestFixtureImpl(true); // "tmp://src/" dir by default
  }

  /**
   * Absolute file paths are prohibited -- the TempDirTestFixture used in these tests will prepend
   * it's own root to the path.
   */
  protected void assertPathIsNotAbsolute(String filePath) {
    assertThat(FileUtil.isAbsolute(filePath)).isFalse();
  }

  /** Creates a file with the specified contents and file path in the test project */
  protected VirtualFile createFile(String filePath) {
    return testFixture.getTempDirFixture().createFile(filePath);
  }

  /** Creates a file with the specified contents and file path in the test project */
  protected VirtualFile createFile(String filePath, String... contentLines) {
    return createFile(filePath, Joiner.on("\n").join(contentLines));
  }

  /** Creates a file with the specified contents and file path in the test project */
  protected VirtualFile createFile(String filePath, String contents) {
    assertPathIsNotAbsolute(filePath);
    try {
      return testFixture.getTempDirFixture().createFile(filePath, contents);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  protected PsiDirectory createPsiDirectory(String path) {
    return getPsiDirectory(createDirectory(path));
  }

  protected VirtualFile createDirectory(String path) {
    assertPathIsNotAbsolute(path);
    try {
      return testFixture.getTempDirFixture().findOrCreateDir(path);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  protected Editor openFileInEditor(PsiFile file) {
    return openFileInEditor(file.getVirtualFile());
  }

  protected Editor openFileInEditor(VirtualFile file) {
    EdtTestUtil.runInEdtAndWait((Runnable) () -> testFixture.openFileInEditor(file));
    return testFixture.getEditor();
  }

  /** @return null if the only item was auto-completed */
  @Nullable
  protected String[] getCompletionItemsAsStrings() {
    LookupElement[] completionItems = testFixture.completeBasic();
    if (completionItems == null) {
      return null;
    }
    return Arrays.stream(completionItems)
        .map(LookupElement::getLookupString)
        .toArray(String[]::new);
  }

  /** @return null if the only item was auto-completed */
  @Nullable
  protected String[] getCompletionItemsAsSuggestionStrings() {
    LookupElement[] completionItems = testFixture.completeBasic();
    if (completionItems == null) {
      return null;
    }
    LookupElementPresentation presentation = new LookupElementPresentation();
    String[] strings = new String[completionItems.length];
    for (int i = 0; i < strings.length; i++) {
      completionItems[i].renderElement(presentation);
      strings[i] = presentation.getItemText();
    }
    return strings;
  }

  /** @return true if a LookupItem was inserted. */
  protected boolean completeIfUnique() {
    LookupElement[] completionItems = testFixture.completeBasic();
    if (completionItems == null) {
      return true;
    }
    if (completionItems.length != 1) {
      return false;
    }
    testFixture.getLookup().setCurrentItem(completionItems[0]);
    testFixture.finishLookup(Lookup.NORMAL_SELECT_CHAR);
    return true;
  }

  /** Simulates a user typing action, at current caret position of file. */
  protected void performTypingAction(PsiFile file, char typedChar) {
    performTypingAction(openFileInEditor(file.getVirtualFile()), typedChar);
  }

  /** Simulates a user typing action, at current caret position of document. */
  protected void performTypingAction(Editor editor, char typedChar) {
    EditorTestUtil.performTypingAction(editor, typedChar);
    getProject().getComponent(PostprocessReformattingAspect.class).doPostponedFormatting();
    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
  }

  /**
   * Clicks the specified button in current document at the current caret position
   *
   * @param action which button to click (see {@link IdeActions})
   */
  protected final void pressButton(final String action) {
    CommandProcessor.getInstance()
        .executeCommand(getProject(), () -> testFixture.performEditorAction(action), "", null);
  }

  protected void setCaretPosition(Editor editor, int lineNumber, int columnNumber) {
    final CaretInfo info = new CaretInfo(new LogicalPosition(lineNumber, columnNumber), null);
    EdtTestUtil.runInEdtAndWait(
        (Runnable)
            () ->
                EditorTestUtil.setCaretsAndSelection(
                    editor, new CaretAndSelectionState(ImmutableList.of(info), null)));
  }

  protected void assertCaretPosition(Editor editor, int lineNumber, int columnNumber) {
    CaretInfo info = new CaretInfo(new LogicalPosition(lineNumber, columnNumber), null);
    EditorTestUtil.verifyCaretAndSelectionState(
        editor, new CaretAndSelectionState(ImmutableList.of(info), null));
  }

  protected Project getProject() {
    return testFixture.getProject();
  }

  protected VirtualFile findFile(String filePath) {
    VirtualFile vf = TempFileSystem.getInstance().findFileByPath(filePath);
    if (vf == null) {
      // this might be a relative path
      vf = testFixture.getTempDirFixture().getFile(filePath);
    }
    return vf;
  }

  protected void assertFileContents(String filePath, String... contentLines) {
    assertFileContents(findFile(filePath), contentLines);
  }

  protected void assertFileContents(VirtualFile file, String... contentLines) {
    assertFileContents(getPsiFile(file), contentLines);
  }

  protected void assertFileContents(PsiFile file, String... contentLines) {
    String contents = Joiner.on("\n").join(contentLines);
    assertThat(file.getText()).isEqualTo(contents);
  }

  /** Creates a file with the specified contents and file path in the test project */
  protected PsiFile createPsiFile(String filePath) {
    return getPsiFile(testFixture.getTempDirFixture().createFile(filePath));
  }

  /** Creates a file with the specified contents and file path in the test project */
  protected PsiFile createPsiFile(String filePath, String... contentLines) {
    return getPsiFile(createFile(filePath, contentLines));
  }

  /** Finds PsiFile, and asserts that it's not null. */
  protected PsiFile getPsiFile(VirtualFile file) {
    return new ReadAction<PsiFile>() {
      @Override
      protected void run(Result<PsiFile> result) throws Throwable {
        PsiFile psiFile = PsiManager.getInstance(getProject()).findFile(file);
        assertThat(psiFile).isNotNull();
        result.setResult(psiFile);
      }
    }.execute().getResultObject();
  }

  /** Finds PsiDirectory, and asserts that it's not null. */
  protected PsiDirectory getPsiDirectory(VirtualFile file) {
    return new ReadAction<PsiDirectory>() {
      @Override
      protected void run(Result<PsiDirectory> result) throws Throwable {
        PsiDirectory psiFile = PsiManager.getInstance(getProject()).findDirectory(file);
        assertThat(psiFile).isNotNull();
        result.setResult(psiFile);
      }
    }.execute().getResultObject();
  }

  protected PsiDirectory renameDirectory(String oldPath, String newPath) {
    try {
      VirtualFile original = findFile(oldPath);
      PsiDirectory originalPsi = PsiManager.getInstance(getProject()).findDirectory(original);
      assertThat(originalPsi).isNotNull();

      VirtualFile destination = testFixture.getTempDirFixture().findOrCreateDir(newPath);
      PsiDirectory destPsi = PsiManager.getInstance(getProject()).findDirectory(destination);
      assertThat(destPsi).isNotNull();

      new MoveDirectoryWithClassesProcessor(
              getProject(), new PsiDirectory[] {originalPsi}, destPsi, true, true, false, null)
          .run();
      return destPsi;

    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  protected void renamePsiElement(PsiNamedElement element, String newName) {
    testFixture.renameElement(element, newName);
  }

  protected void handleRename(PsiReference reference, String newName) {
    doRenameOperation(() -> reference.handleElementRename(newName));
  }

  protected void doRenameOperation(Runnable renameOp) {
    ApplicationManager.getApplication()
        .runWriteAction(() -> CommandProcessor.getInstance().runUndoTransparentAction(renameOp));
  }

  protected static <T> List<T> findAllReferencingElementsOfType(
      PsiElement target, Class<T> referenceType) {
    return Arrays.stream(FindUsages.findAllReferences(target))
        .map(PsiReference::getElement)
        .filter(referenceType::isInstance)
        .map(e -> (T) e)
        .collect(Collectors.toList());
  }

  protected void mockBlazeProjectDataManager(BlazeProjectData data) {
    BlazeProjectDataManager mockProjectDataManager =
        new BlazeProjectDataManager() {
          @Nullable
          @Override
          public BlazeProjectData getBlazeProjectData() {
            return data;
          }

          @Override
          public BlazeSyncPlugin.ModuleEditor editModules() {
            return ModuleEditorProvider.getInstance()
                .getModuleEditor(
                    getProject(),
                    BlazeImportSettingsManager.getInstance(getProject()).getImportSettings());
          }
        };
    registerProjectService(BlazeProjectDataManager.class, mockProjectDataManager);
  }

  protected <T> void registerApplicationService(Class<T> key, T implementation) {
    ServiceHelper.registerApplicationService(key, implementation, getTestRootDisposable());
  }

  protected <T> void registerProjectService(Class<T> key, T implementation) {
    ServiceHelper.registerProjectService(
        getProject(), key, implementation, getTestRootDisposable());
  }

  protected <T> void registerExtension(ExtensionPointName<T> name, T instance) {
    ServiceHelper.registerExtension(name, instance, getTestRootDisposable());
  }

  /** Redirects file system checks via the TempFileSystem used for these tests. */
  private static class TempFileAttributeProvider extends FileAttributeProvider {

    final TempFileSystem fileSystem = TempFileSystem.getInstance();

    @Override
    public boolean exists(File file) {
      VirtualFile vf = getVirtualFile(file);
      return vf != null && vf.exists();
    }

    @Override
    public boolean isDirectory(File file) {
      VirtualFile vf = getVirtualFile(file);
      return vf != null && vf.isDirectory();
    }

    @Override
    public boolean isFile(File file) {
      VirtualFile vf = getVirtualFile(file);
      return vf != null && vf.exists() && !vf.isDirectory();
    }

    private VirtualFile getVirtualFile(File file) {
      return fileSystem.findFileByPath(file.getPath());
    }
  }
}
