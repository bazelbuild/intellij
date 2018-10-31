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

import com.google.idea.blaze.base.io.FileOperationProvider;
import com.google.idea.blaze.base.io.InputStreamProvider;
import com.google.idea.blaze.base.io.VirtualFileSystemProvider;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.settings.BlazeImportSettings;
import com.google.idea.blaze.base.settings.BlazeImportSettingsManager;
import com.google.idea.blaze.base.settings.BuildSystem;
import com.google.idea.blaze.base.sync.SyncCache;
import com.google.idea.testing.EdtRule;
import com.google.idea.testing.IntellijTestSetupRule;
import com.google.idea.testing.ServiceHelper;
import com.google.idea.testing.VerifyRequiredPluginsEnabled;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.TestFixtureBuilder;
import com.intellij.testFramework.fixtures.impl.LightTempDirTestFixtureImpl;
import java.io.File;
import java.io.FileNotFoundException;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

/** Base test class for blaze integration tests. {@link UsefulTestCase} */
public abstract class BlazeIntegrationTestCase {

  /** Test rule that ensures tests do not run on Windows (see http://b.android.com/222904) */
  public static class IgnoreOnWindowsRule implements TestRule {
    @Override
    public Statement apply(Statement base, Description description) {
      if (SystemInfo.isWindows) {
        return new Statement() {
          @Override
          public void evaluate() throws Throwable {
            System.out.println(
                "Test \""
                    + description.getDisplayName()
                    + "\" does not run on Windows (see http://b.android.com/222904)");
          }
        };
      }
      return base;
    }
  }

  @Rule public final IgnoreOnWindowsRule rule = new IgnoreOnWindowsRule();
  @Rule public final IntellijTestSetupRule setupRule = new IntellijTestSetupRule();
  @Rule public final TestRule testRunWrapper = runTestsOnEdt() ? new EdtRule() : null;

  protected CodeInsightTestFixture testFixture;
  protected WorkspaceRoot workspaceRoot;
  protected VirtualFile projectDataDirectory;
  protected TestFileSystem fileSystem;
  protected WorkspaceFileSystem workspace;

  @Before
  public final void setUp() throws Exception {
    testFixture = createTestFixture();
    testFixture.setUp();
    fileSystem =
        new TestFileSystem(getProject(), testFixture.getTempDirFixture(), isLightTestCase());

    runWriteAction(
        () -> {
          ProjectJdkTable.getInstance().addJdk(IdeaTestUtil.getMockJdk18());
          VirtualFile workspaceRootVirtualFile = fileSystem.createDirectory("workspace");
          workspaceRoot = new WorkspaceRoot(new File(workspaceRootVirtualFile.getPath()));
          projectDataDirectory = fileSystem.createDirectory("project-data-dir");
          workspace = new WorkspaceFileSystem(workspaceRoot, fileSystem);
        });

    BlazeImportSettingsManager.getInstance(getProject())
        .setImportSettings(
            new BlazeImportSettings(
                workspaceRoot.toString(),
                "test-project",
                projectDataDirectory.getPath(),
                workspaceRoot.fileForPath(new WorkspacePath("project-view-file")).getPath(),
                buildSystem()));

    registerApplicationService(
        InputStreamProvider.class,
        file -> {
          VirtualFile vf = fileSystem.findFile(file.getPath());
          if (vf == null) {
            throw new FileNotFoundException();
          }
          return vf.getInputStream();
        });

    if (isLightTestCase()) {
      registerApplicationService(
          FileOperationProvider.class, new TestFileSystem.MockFileOperationProvider());
      registerApplicationService(
          VirtualFileSystemProvider.class, new TestFileSystem.TempVirtualFileSystemProvider());
    }

    String requiredPlugins = System.getProperty("idea.required.plugins.id");
    if (requiredPlugins != null) {
      VerifyRequiredPluginsEnabled.runCheck(requiredPlugins.split(","));
    }
  }

  @After
  public final void tearDown() throws Exception {
    SyncCache.getInstance(getProject()).clear();
    runWriteAction(
        () -> {
          ProjectJdkTable table = ProjectJdkTable.getInstance();
          for (Sdk sdk : ProjectJdkTable.getInstance().getAllJdks()) {
            table.removeJdk(sdk);
          }
        });
    testFixture.tearDown();
    testFixture = null;
  }

  private static void runWriteAction(Runnable writeAction) {
    EdtTestUtil.runInEdtAndWait(
        () -> ApplicationManager.getApplication().runWriteAction(writeAction));
  }

  private CodeInsightTestFixture createTestFixture() {
    IdeaTestFixtureFactory factory = IdeaTestFixtureFactory.getFixtureFactory();

    if (isLightTestCase()) {
      TestFixtureBuilder<IdeaProjectTestFixture> fixtureBuilder =
          factory.createLightFixtureBuilder(LightCodeInsightFixtureTestCase.JAVA_8);
      IdeaProjectTestFixture lightFixture = fixtureBuilder.getFixture();
      return factory.createCodeInsightFixture(lightFixture, new LightTempDirTestFixtureImpl(true));
    }

    TestFixtureBuilder<IdeaProjectTestFixture> fixtureBuilder =
        factory.createFixtureBuilder("test-project");
    return factory.createCodeInsightFixture(fixtureBuilder.getFixture());
  }

  /**
   * Override to back this test with a heavy test fixture, which will actually modify files on disk
   * instead of keeping everything in memory like a light test fixture does. This can hurt test
   * performance, though we aren't sure to what extent (b/117435202).
   */
  protected boolean isLightTestCase() {
    return true;
  }

  /** Override to run tests with bazel specified as the project's build system. */
  protected BuildSystem buildSystem() {
    return BuildSystem.Blaze;
  }

  /** Override to run tests off the EDT. */
  protected boolean runTestsOnEdt() {
    return true;
  }

  protected Project getProject() {
    return testFixture.getProject();
  }

  protected Disposable getTestRootDisposable() {
    return setupRule.testRootDisposable;
  }

  protected <T> void registerApplicationService(Class<T> key, T implementation) {
    ServiceHelper.registerApplicationService(key, implementation, getTestRootDisposable());
  }

  protected <T> void registerApplicationComponent(Class<T> key, T implementation) {
    ServiceHelper.registerApplicationComponent(key, implementation, getTestRootDisposable());
  }

  protected <T> void registerProjectService(Class<T> key, T implementation) {
    ServiceHelper.registerProjectService(
        getProject(), key, implementation, getTestRootDisposable());
  }

  public <T> void registerProjectComponent(Class<T> key, T implementation) {
    ServiceHelper.registerProjectComponent(
        getProject(), key, implementation, getTestRootDisposable());
  }

  protected <T> void registerExtension(ExtensionPointName<T> name, T instance) {
    ServiceHelper.registerExtension(name, instance, getTestRootDisposable());
  }
}
