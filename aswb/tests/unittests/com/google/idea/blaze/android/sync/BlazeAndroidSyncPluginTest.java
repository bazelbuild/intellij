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

package com.google.idea.blaze.android.sync;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.android.projectview.AndroidSdkPlatformSection;
import com.google.idea.blaze.android.sdk.BlazeSdkProvider;
import com.google.idea.blaze.android.sdk.MockBlazeSdkProvider;
import com.google.idea.blaze.android.sync.model.AndroidSdkPlatform;
import com.google.idea.blaze.android.sync.model.BlazeAndroidSyncData;
import com.google.idea.blaze.base.BlazeTestCase;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.MockBlazeProjectDataBuilder;
import com.google.idea.blaze.base.model.SyncState;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.model.primitives.WorkspaceType;
import com.google.idea.blaze.base.projectview.ProjectView;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.projectview.section.ScalarSection;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.ErrorCollector;
import com.google.idea.blaze.base.scope.output.IssueOutput;
import com.google.idea.blaze.base.sync.projectview.WorkspaceLanguageSettings;
import com.google.idea.blaze.java.sync.model.BlazeJavaImportResult;
import com.google.idea.blaze.java.sync.model.BlazeJavaSyncData;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkTypeId;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.openapi.roots.OrderEnumerator;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.module.JpsModuleSourceRootType;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link com.google.idea.blaze.android.sync.BlazeAndroidSyncPlugin} */
@RunWith(JUnit4.class)
public class BlazeAndroidSyncPluginTest extends BlazeTestCase {
  private static final Sdk MOCK_ANDROID_SDK_26 = mockSdk("android-sdk-26");
  private static final Sdk MOCK_ANDROID_SDK_28 = mockSdk("android-sdk-28");
  private final WorkspaceRoot workspaceRoot = new WorkspaceRoot(new File("/"));
  private final BlazeAndroidSyncPlugin syncPlugin = new BlazeAndroidSyncPlugin();
  private BlazeContext context;
  private ProjectViewSet projectViewSet;

  /**
   * Initialized blaze context, project view set, and registers the following services:
   *
   * <p>A mock sdk provider with 2 registered SDKs: android-26 and android-28. 2 SDKs are registered
   * because the test will make use of both to verify the correct one is selected.
   *
   * <p>A {@link MockProjectRootManagerEx} service. Note that it's registered on the {@link
   * ProjectRootManager} component instead of {@link ProjectRootManagerEx}. This is due to the way
   * {@link ProjectRootManagerEx} obtains its own instance. See {@link
   * ProjectRootManagerEx#getInstance(Project)} for more details.
   *
   * <p>A {@link MockLanguageLevelProjectExtension} service. This is to stop {@link
   * BlazeAndroidSyncPlugin#setProjectSdkAndLanguageLevel(Project, Sdk, LanguageLevel)} from
   * throwing NPEs because it makes use of that service.
   */
  @Override
  protected void initTest(Container applicationServices, Container projectServices) {
    super.initTest(applicationServices, projectServices);
    MockBlazeSdkProvider mockSdkProvider = new MockBlazeSdkProvider();
    mockSdkProvider.addSdk("android-26", MOCK_ANDROID_SDK_26);
    mockSdkProvider.addSdk("android-28", MOCK_ANDROID_SDK_28);
    applicationServices.register(BlazeSdkProvider.class, mockSdkProvider);

    projectServices.register(ProjectRootManager.class, new MockProjectRootManagerEx());

    projectServices.register(
        LanguageLevelProjectExtension.class, new MockLanguageLevelProjectExtension());

    context = new BlazeContext();
    context.addOutputSink(IssueOutput.class, new ErrorCollector());

    projectViewSet =
        ProjectViewSet.builder()
            .add(
                ProjectView.builder()
                    .add(ScalarSection.builder(AndroidSdkPlatformSection.KEY).set("android-28"))
                    .build())
            .build();
  }

  @Test
  public void testUpdateProjectSdkWithSyncData() {
    // Setup.
    SyncState syncStateWithAndroidSdk26AndJava9 =
        new SyncState.Builder()
            .put(new BlazeAndroidSyncData(null, new AndroidSdkPlatform("android-26", 0)))
            .put(
                new BlazeJavaSyncData(
                    new BlazeJavaImportResult(
                        ImmutableList.of(),
                        ImmutableMap.of(),
                        ImmutableList.of(),
                        ImmutableSet.of(),
                        "9"),
                    null))
            .build();

    BlazeProjectData blazeProjectData =
        MockBlazeProjectDataBuilder.builder(workspaceRoot)
            .setWorkspaceLanguageSettings(
                new WorkspaceLanguageSettings(WorkspaceType.ANDROID, ImmutableSet.of()))
            .setSyncState(syncStateWithAndroidSdk26AndJava9)
            .build();

    // Perform.
    syncPlugin.updateProjectSdk(project, context, projectViewSet, null, blazeProjectData);

    // Verify.
    ProjectRootManagerEx rootManager = ProjectRootManagerEx.getInstanceEx(project);
    LanguageLevel languageLevel =
        LanguageLevelProjectExtension.getInstance(project).getLanguageLevel();

    // Should return android-26 even though project view says android-28 because data returned from
    // sync takes higher priority.
    assertThat(rootManager.getProjectSdk().getName()).isEqualTo("android-sdk-26");
    // Defaults to JDK 1.8, but sync result specifies 1.9, which takes higher priority.
    assertThat(languageLevel).isEqualTo(LanguageLevel.JDK_1_9);
  }

  @Test
  public void testUpdateProjectSdkWithoutSyncData() {
    // Setup.
    BlazeProjectData blazeProjectData =
        MockBlazeProjectDataBuilder.builder(workspaceRoot)
            .setWorkspaceLanguageSettings(
                new WorkspaceLanguageSettings(WorkspaceType.ANDROID, ImmutableSet.of()))
            .build();

    // Perform.
    syncPlugin.updateProjectSdk(project, context, projectViewSet, null, blazeProjectData);

    // Verify.
    ProjectRootManagerEx rootManager = ProjectRootManagerEx.getInstanceEx(project);
    LanguageLevel languageLevel =
        LanguageLevelProjectExtension.getInstance(project).getLanguageLevel();

    // Even when sync data is null, the project sdk should still be available.
    assertThat(rootManager.getProjectSdk().getName()).isEqualTo("android-sdk-28");
    assertThat(languageLevel).isEqualTo(LanguageLevel.JDK_1_8);
  }

  @Test
  public void testUpdateProjectSdkWithoutSyncDataDoesNotOverrideSdkIfOneAlreadyExists() {
    // Setup.
    BlazeProjectData blazeProjectData =
        MockBlazeProjectDataBuilder.builder(workspaceRoot)
            .setWorkspaceLanguageSettings(
                new WorkspaceLanguageSettings(WorkspaceType.ANDROID, ImmutableSet.of()))
            .build();
    ProjectRootManagerEx.getInstanceEx(project).setProjectSdk(MOCK_ANDROID_SDK_26);

    // Perform.
    syncPlugin.updateProjectSdk(project, context, projectViewSet, null, blazeProjectData);

    // Verify.
    ProjectRootManagerEx rootManager = ProjectRootManagerEx.getInstanceEx(project);
    LanguageLevel languageLevel =
        LanguageLevelProjectExtension.getInstance(project).getLanguageLevel();

    // Even when sync data is null, the project sdk should still be available.  In this case
    // an sdk is already available, so it's not reset from project view.
    assertThat(rootManager.getProjectSdk().getName()).isEqualTo("android-sdk-26");
    assertThat(languageLevel).isNull();
  }

  private static Sdk mockSdk(String sdkName) {
    SdkTypeId sdkType = mock(SdkTypeId.class);
    when(sdkType.getName()).thenReturn("Android SDK");
    Sdk sdk = mock(Sdk.class);
    when(sdk.getName()).thenReturn(sdkName);
    when(sdk.getSdkType()).thenReturn(sdkType);
    return sdk;
  }

  /** Stores language level so that it can be obtained later for verification */
  private static class MockLanguageLevelProjectExtension extends LanguageLevelProjectExtension {
    LanguageLevel languageLevel;

    @NotNull
    @Override
    public LanguageLevel getLanguageLevel() {
      return languageLevel;
    }

    @Override
    public void setLanguageLevel(@NotNull LanguageLevel languageLevel) {
      this.languageLevel = languageLevel;
    }
  }

  /** Stores a project sdk so that it can be obtained later for verification. */
  private static class MockProjectRootManagerEx extends ProjectRootManagerEx {
    Sdk projectSdk;

    @Nullable
    @Override
    public Sdk getProjectSdk() {
      return projectSdk;
    }

    @Override
    public void setProjectSdk(@Nullable Sdk sdk) {
      projectSdk = sdk;
    }

    /* The below methods are not used and irrelevant to this test */
    @Override
    public void addProjectJdkListener(@NotNull ProjectJdkListener projectJdkListener) {}

    @Override
    public void removeProjectJdkListener(@NotNull ProjectJdkListener projectJdkListener) {}

    @Override
    public void makeRootsChange(@NotNull Runnable runnable, boolean b, boolean b1) {}

    @Override
    public void markRootsForRefresh() {}

    @Override
    public void mergeRootsChangesDuring(@NotNull Runnable runnable) {}

    @Override
    public void clearScopesCachesForModules() {}

    @NotNull
    @Override
    public ProjectFileIndex getFileIndex() {
      return null;
    }

    @NotNull
    @Override
    public OrderEnumerator orderEntries() {
      return null;
    }

    @NotNull
    @Override
    public OrderEnumerator orderEntries(@NotNull Collection<? extends Module> collection) {
      return null;
    }

    @Override
    public VirtualFile[] getContentRootsFromAllModules() {
      return new VirtualFile[0];
    }

    @NotNull
    @Override
    public List<String> getContentRootUrls() {
      return null;
    }

    @NotNull
    @Override
    public VirtualFile[] getContentRoots() {
      return new VirtualFile[0];
    }

    @NotNull
    @Override
    public VirtualFile[] getContentSourceRoots() {
      return new VirtualFile[0];
    }

    @NotNull
    @Override
    public List<VirtualFile> getModuleSourceRoots(
        @NotNull Set<? extends JpsModuleSourceRootType<?>> set) {
      return null;
    }

    @Override
    public String getProjectSdkName() {
      return null;
    }

    @Override
    public void setProjectSdkName(String s) {}
  }
}
