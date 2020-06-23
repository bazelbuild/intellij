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
package com.google.idea.blaze.android.functional;

import static com.google.common.truth.Truth.assertThat;
import static com.google.idea.blaze.android.targetmapbuilder.NbAndroidTarget.android_binary;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.android.tools.idea.run.ApkProvisionException;
import com.google.common.collect.ImmutableList;
import com.google.devtools.build.lib.rules.android.deployinfo.AndroidDeployInfoOuterClass.AndroidDeployInfo;
import com.google.idea.blaze.android.BlazeAndroidIntegrationTestCase;
import com.google.idea.blaze.android.MessageCollector;
import com.google.idea.blaze.android.MockSdkUtil;
import com.google.idea.blaze.android.run.deployinfo.BlazeAndroidDeployInfo;
import com.google.idea.blaze.android.run.deployinfo.BlazeApkDeployInfoProtoHelper;
import com.google.idea.blaze.android.run.deployinfo.BlazeApkDeployInfoProtoHelper.GetDeployInfoException;
import com.google.idea.blaze.android.run.runner.BlazeAndroidDeviceSelector.DeviceSession;
import com.google.idea.blaze.android.run.runner.BlazeApkBuildStepNormalBuild;
import com.google.idea.blaze.base.async.process.ExternalTask;
import com.google.idea.blaze.base.async.process.ExternalTaskProvider;
import com.google.idea.blaze.base.command.buildresult.BuildResultHelper;
import com.google.idea.blaze.base.command.buildresult.BuildResultHelper.GetArtifactsException;
import com.google.idea.blaze.base.command.buildresult.BuildResultHelperProvider;
import com.google.idea.blaze.base.command.buildresult.ParsedBepOutput;
import com.google.idea.blaze.base.command.info.BlazeInfo;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.output.IssueOutput;
import com.google.idea.blaze.base.sync.aspects.BuildResult;
import com.intellij.openapi.project.Project;
import java.io.File;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Integration tests for {@link BlazeApkBuildStepNormalBuild} */
@RunWith(JUnit4.class)
public class BlazeApkBuildStepNormalBuildIntegrationTest extends BlazeAndroidIntegrationTestCase {
  /** Exposed to test methods to toggle presence of execroot */
  private BuildResultHelper mockBuildResultHelper;

  @Before
  public void setupProject() {
    setProjectView(
        "directories:",
        "  java/com/foo/app",
        "targets:",
        "  //java/com/foo/app:app",
        "android_sdk_platform: android-27");
    MockSdkUtil.registerSdk(workspace, "27");

    workspace.createFile(
        new WorkspacePath("java/com/foo/app/MainActivity.java"),
        "package com.foo.app",
        "import android.app.Activity;",
        "public class MainActivity extends Activity {}");

    setTargetMap(android_binary("//java/com/foo/app:app").src("MainActivity.java"));
    runFullBlazeSyncWithNoIssues();
  }

  /** Setup build result helper to return BEP output with test execroot by default. */
  @Before
  public void setupBuildResultHelperProvider() throws GetArtifactsException {
    mockBuildResultHelper = mock(BuildResultHelper.class);
    when(mockBuildResultHelper.getBuildOutput())
        .thenReturn(new ParsedBepOutput(null, getExecRoot(), null, null, 0, BuildResult.SUCCESS));
    registerExtension(
        BuildResultHelperProvider.EP_NAME,
        new BuildResultHelperProvider() {
          @Override
          public Optional<BuildResultHelper> doCreate(Project project) {
            return Optional.of(mockBuildResultHelper);
          }

          @Override
          public Optional<BuildResultHelper> doCreateForSync(Project project, BlazeInfo blazeInfo) {
            return Optional.empty();
          }
        });
  }

  @Test
  public void deployInfoBuiltCorrectly() throws GetDeployInfoException, ApkProvisionException {
    Label buildTarget = Label.create("//java/com/foo/app:app");
    BlazeContext context = new BlazeContext();
    ImmutableList<String> blazeFlags = ImmutableList.of("some_blaze_flag", "some_other_flag");

    // Setup interceptor for fake running of blaze commands and capture details.
    ExternalTaskInterceptor externalTaskInterceptor = new ExternalTaskInterceptor();
    registerApplicationService(ExternalTaskProvider.class, externalTaskInterceptor);

    // Return fake deploy info proto and mocked deploy info data object.
    AndroidDeployInfo fakeProto = AndroidDeployInfo.newBuilder().build();
    BlazeAndroidDeployInfo mockDeployInfo = mock(BlazeAndroidDeployInfo.class);
    BlazeApkDeployInfoProtoHelper helper = mock(BlazeApkDeployInfoProtoHelper.class);
    when(helper.readDeployInfoProtoForTarget(eq(buildTarget), any(BuildResultHelper.class), any()))
        .thenReturn(fakeProto);
    when(helper.extractDeployInfoAndInvalidateManifests(
            eq(getProject()), eq(new File(getExecRoot())), eq(fakeProto)))
        .thenReturn(mockDeployInfo);

    // Perform
    BlazeApkBuildStepNormalBuild buildStep =
        new BlazeApkBuildStepNormalBuild(getProject(), buildTarget, blazeFlags, helper);
    buildStep.build(context, new DeviceSession(null, null, null));

    // Verify
    assertThat(buildStep.getDeployInfo()).isNotNull();
    assertThat(buildStep.getDeployInfo()).isEqualTo(mockDeployInfo);
    assertThat(externalTaskInterceptor.context).isEqualTo(context);
    assertThat(externalTaskInterceptor.command).contains(buildTarget.toString());
    assertThat(externalTaskInterceptor.command).contains("--output_groups=+android_deploy_info");
    assertThat(externalTaskInterceptor.command).containsAllIn(blazeFlags);
  }

  @Test
  public void exceptionDuringDeployInfoExtraction() throws GetDeployInfoException {
    Label buildTarget = Label.create("//java/com/foo/app:app");

    MessageCollector messageCollector = new MessageCollector();
    BlazeContext context = new BlazeContext();
    context.addOutputSink(IssueOutput.class, messageCollector);

    // Make blaze command invocation always pass.
    registerApplicationService(ExternalTaskProvider.class, builder -> scopes -> 0);

    // Return fake deploy info proto and mocked deploy info data object.
    AndroidDeployInfo fakeProto = AndroidDeployInfo.newBuilder().build();
    BlazeApkDeployInfoProtoHelper helper = mock(BlazeApkDeployInfoProtoHelper.class);
    when(helper.readDeployInfoProtoForTarget(eq(buildTarget), any(BuildResultHelper.class), any()))
        .thenReturn(fakeProto);
    when(helper.extractDeployInfoAndInvalidateManifests(any(), any(), any()))
        .thenThrow(new GetDeployInfoException("Fake Exception"));

    // Perform
    BlazeApkBuildStepNormalBuild buildStep =
        new BlazeApkBuildStepNormalBuild(getProject(), buildTarget, ImmutableList.of(), helper);
    buildStep.build(context, new DeviceSession(null, null, null));

    // Verify
    assertThat(context.hasErrors()).isTrue();
    assertThat(messageCollector.getMessages())
        .contains("Could not read apk deploy info from build: Fake Exception");
  }

  @Test
  public void blazeCommandFailed() throws GetDeployInfoException {
    Label buildTarget = Label.create("//java/com/foo/app:app");

    MessageCollector messageCollector = new MessageCollector();
    BlazeContext context = new BlazeContext();
    context.addOutputSink(IssueOutput.class, messageCollector);

    // Return a non-zero value to indicate blaze command run failure.
    registerApplicationService(ExternalTaskProvider.class, builder -> scopes -> 1337);

    // Return fake deploy info proto and mocked deploy info data object.
    AndroidDeployInfo fakeProto = AndroidDeployInfo.newBuilder().build();
    BlazeAndroidDeployInfo mockDeployInfo = mock(BlazeAndroidDeployInfo.class);
    BlazeApkDeployInfoProtoHelper helper = mock(BlazeApkDeployInfoProtoHelper.class);
    when(helper.readDeployInfoProtoForTarget(eq(buildTarget), any(BuildResultHelper.class), any()))
        .thenReturn(fakeProto);
    when(helper.extractDeployInfoAndInvalidateManifests(
            eq(getProject()), eq(new File(getExecRoot())), eq(fakeProto)))
        .thenReturn(mockDeployInfo);

    // Perform
    BlazeApkBuildStepNormalBuild buildStep =
        new BlazeApkBuildStepNormalBuild(getProject(), buildTarget, ImmutableList.of(), helper);
    buildStep.build(context, new DeviceSession(null, null, null));

    // Verify
    assertThat(context.hasErrors()).isTrue();
    assertThat(messageCollector.getMessages())
        .contains("Blaze build failed. See Blaze Console for details.");
  }

  @Test
  public void nullExecRoot() throws GetDeployInfoException, GetArtifactsException {
    Label buildTarget = Label.create("//java/com/foo/app:app");
    ImmutableList<String> blazeFlags = ImmutableList.of("some_blaze_flag", "some_other_flag");

    MessageCollector messageCollector = new MessageCollector();
    BlazeContext context = new BlazeContext();
    context.addOutputSink(IssueOutput.class, messageCollector);

    // Return null execroot
    when(mockBuildResultHelper.getBuildOutput())
        .thenReturn(new ParsedBepOutput(null, null, null, null, 0, BuildResult.SUCCESS));

    // Setup interceptor for fake running of blaze commands and capture details.
    ExternalTaskInterceptor externalTaskInterceptor = new ExternalTaskInterceptor();
    registerApplicationService(ExternalTaskProvider.class, externalTaskInterceptor);

    // Return fake deploy info proto and mocked deploy info data object.
    AndroidDeployInfo fakeProto = AndroidDeployInfo.newBuilder().build();
    BlazeAndroidDeployInfo mockDeployInfo = mock(BlazeAndroidDeployInfo.class);
    BlazeApkDeployInfoProtoHelper helper = mock(BlazeApkDeployInfoProtoHelper.class);
    when(helper.readDeployInfoProtoForTarget(eq(buildTarget), any(BuildResultHelper.class), any()))
        .thenReturn(fakeProto);
    when(helper.extractDeployInfoAndInvalidateManifests(
            eq(getProject()), eq(new File(getExecRoot())), eq(fakeProto)))
        .thenReturn(mockDeployInfo);

    // Perform
    BlazeApkBuildStepNormalBuild buildStep =
        new BlazeApkBuildStepNormalBuild(getProject(), buildTarget, blazeFlags, helper);
    buildStep.build(context, new DeviceSession(null, null, null));

    // Verify
    assertThat(context.hasErrors()).isTrue();
    assertThat(messageCollector.getMessages()).contains("Could not locate execroot!");
  }

  /** Saves the latest blaze command and context for later verification. */
  private static class ExternalTaskInterceptor implements ExternalTaskProvider {
    ImmutableList<String> command;
    BlazeContext context;

    @Override
    public ExternalTask build(ExternalTask.Builder builder) {
      command = builder.command.build();
      context = builder.context;
      return scopes -> 0;
    }
  }
}
