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
package com.google.idea.blaze.base.sync.sharding;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.truth.Truth.assertThat;
import static com.google.idea.blaze.base.sync.sharding.BlazeBuildTargetSharder.shardAutomatically;
import static java.nio.charset.StandardCharsets.UTF_8;
import static junit.framework.TestCase.fail;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.io.ByteStreams;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.idea.blaze.base.BlazeTestCase;
import com.google.idea.blaze.base.async.process.ExternalTask;
import com.google.idea.blaze.base.async.process.ExternalTaskProvider;
import com.google.idea.blaze.base.bazel.BazelBuildSystemProvider;
import com.google.idea.blaze.base.bazel.BuildSystemProvider;
import com.google.idea.blaze.base.command.BuildFlagsProvider;
import com.google.idea.blaze.base.console.BlazeConsoleLineProcessorProvider;
import com.google.idea.blaze.base.console.BlazeConsoleLineProcessorProvider.GeneralProvider;
import com.google.idea.blaze.base.io.FileOperationProvider;
import com.google.idea.blaze.base.logging.utils.ShardStats;
import com.google.idea.blaze.base.logging.utils.ShardStats.ShardingApproach;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.GenericBlazeRules;
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.TargetExpression;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.model.primitives.WorkspaceType;
import com.google.idea.blaze.base.prefetch.PrefetchService;
import com.google.idea.blaze.base.projectview.ProjectView;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.projectview.section.ScalarSection;
import com.google.idea.blaze.base.projectview.section.sections.ShardBlazeBuildsSection;
import com.google.idea.blaze.base.projectview.section.sections.TargetShardSizeSection;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.BlazeScope;
import com.google.idea.blaze.base.settings.BlazeImportSettingsManager;
import com.google.idea.blaze.base.settings.BuildBinaryType;
import com.google.idea.blaze.base.sync.BlazeBuildParams;
import com.google.idea.blaze.base.sync.BlazeSyncPlugin;
import com.google.idea.blaze.base.sync.sharding.BlazeBuildTargetSharder.ShardedTargetsResult;
import com.google.idea.blaze.base.sync.workspace.WorkspacePathResolverImpl;
import com.google.idea.common.experiments.ExperimentService;
import com.google.idea.common.experiments.MockExperimentService;
import com.intellij.openapi.project.Project;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Collection;
import java.util.List;
import java.util.OptionalInt;
import java.util.Set;
import javax.annotation.Nullable;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link BlazeBuildTargetSharder}. */
@RunWith(JUnit4.class)
public class BlazeBuildTargetSharderTest extends BlazeTestCase {
  private final FakeBuildBatchingService fakeBuildBatchingService = new FakeBuildBatchingService();
  private final MockExperimentService mockExperimentService = new MockExperimentService();
  private final FakeWildCardTargetExpanderExternalTaskProvider
      fakeWildCardTargetExpanderExternalTaskProvider =
          new FakeWildCardTargetExpanderExternalTaskProvider();

  @Override
  protected void initTest(Container applicationServices, Container projectServices) {
    registerExtensionPoint(BuildFlagsProvider.EP_NAME, BuildFlagsProvider.class);
    registerExtensionPoint(BuildBatchingService.EP_NAME, BuildBatchingService.class)
        .registerExtension(fakeBuildBatchingService, testDisposable);
    registerExtensionPoint(TargetShardSizeLimit.EP_NAME, TargetShardSizeLimit.class)
        .registerExtension(OptionalInt::empty, testDisposable);
    registerExtensionPoint(BuildSystemProvider.EP_NAME, BuildSystemProvider.class)
        .registerExtension(new BazelBuildSystemProvider(), testDisposable);
    registerExtensionPoint(BlazeSyncPlugin.EP_NAME, BlazeSyncPlugin.class)
        .registerExtension(new FakeBlazeSyncPlugin(), testDisposable);
    registerExtensionPoint(Kind.Provider.EP_NAME, Kind.Provider.class)
        .registerExtension(new GenericBlazeRules(), testDisposable);
    registerExtensionPoint(
            BlazeConsoleLineProcessorProvider.EP_NAME, BlazeConsoleLineProcessorProvider.class)
        .registerExtension(new GeneralProvider(), testDisposable);

    applicationServices.register(ExperimentService.class, mockExperimentService);
    applicationServices.register(
        ExternalTaskProvider.class, fakeWildCardTargetExpanderExternalTaskProvider);
    applicationServices.register(PrefetchService.class, new FakePrefetchService());
    applicationServices.register(FileOperationProvider.class, new FakeFileOperationProvider());
    applicationServices.register(Kind.ApplicationState.class, new Kind.ApplicationState());

    projectServices.register(
        BlazeImportSettingsManager.class, new BlazeImportSettingsManager(getProject()));
  }

  @Test
  public void shardSingleTargets_testExcludedTargetsAreRemoved() {
    List<TargetExpression> targets =
        ImmutableList.of(
            target("//java/com/google:one"),
            target("//java/com/google:two"),
            target("//java/com/google:three"),
            target("-//java/com/google:one"),
            target("-//java/com/google:three"),
            target("-//java/com/google:six"));
    ShardedTargetList shards =
        BlazeBuildTargetSharder.shardSingleTargets(
            targets, BuildBinaryType.BLAZE, /* shardSize= */ 3);

    assertThat(shards.shardedTargets).hasSize(1);
    assertThat(shards.shardedTargets.get(0)).containsExactly(target("//java/com/google:two"));
  }

  @Test
  public void shardSingleTargets_testWildcardExcludesHandled() {
    List<TargetExpression> targets =
        ImmutableList.of(
            target("//java/com/foo:target"),
            target("//java/com/bar:target"),
            target("//java/com/baz:target"),
            target("//java/com/foo:other"),
            target("-//java/com/foo/..."));
    ShardedTargetList shards =
        BlazeBuildTargetSharder.shardSingleTargets(
            targets, BuildBinaryType.BLAZE, /* shardSize= */ 2);
    assertThat(shards.shardedTargets).hasSize(1);
    assertThat(shards.shardedTargets.get(0))
        .containsExactly(target("//java/com/bar:target"), target("//java/com/baz:target"))
        .inOrder();
  }

  @Test
  public void shardSingleTargets_testExcludedThenIncludedTargetsAreRetained() {
    List<TargetExpression> targets =
        ImmutableList.of(
            target("//java/com/google:one"),
            target("-//java/com/google:one"),
            target("//java/com/google:one"),
            target("-//java/com/google:two"),
            target("//java/com/google:two"));
    ShardedTargetList shards =
        BlazeBuildTargetSharder.shardSingleTargets(
            targets, BuildBinaryType.BLAZE, /* shardSize= */ 3);
    assertThat(shards.shardedTargets).hasSize(1);
    assertThat(shards.shardedTargets.get(0))
        .containsExactly(target("//java/com/google:one"), target("//java/com/google:two"));
  }

  @Test
  public void shardTargetsRetainingOrdering_testShardSizeRespected() {
    List<TargetExpression> targets =
        ImmutableList.of(
            target("//java/com/google:one"),
            target("//java/com/google:two"),
            target("//java/com/google:three"),
            target("//java/com/google:four"),
            target("//java/com/google:five"));
    List<ImmutableList<TargetExpression>> shards =
        BlazeBuildTargetSharder.shardTargetsRetainingOrdering(targets, 2);
    assertThat(shards).hasSize(3);
    assertThat(shards.get(0)).hasSize(2);
    assertThat(shards.get(1)).hasSize(2);
    assertThat(shards.get(2)).hasSize(1);

    shards = BlazeBuildTargetSharder.shardTargetsRetainingOrdering(targets, 4);
    assertThat(shards).hasSize(2);
    assertThat(shards.get(0)).hasSize(4);
    assertThat(shards.get(1)).hasSize(1);

    shards = BlazeBuildTargetSharder.shardTargetsRetainingOrdering(targets, 100);
    assertThat(shards).hasSize(1);
    assertThat(shards.get(0)).hasSize(5);
  }

  @Test
  public void shardTargetsRetainingOrdering_testAllSubsequentExcludedTargetsAppendedToShards() {
    List<TargetExpression> targets =
        ImmutableList.of(
            target("//java/com/google:one"),
            target("-//java/com/google:two"),
            target("//java/com/google:three"),
            target("-//java/com/google:four"),
            target("//java/com/google:five"),
            target("-//java/com/google:six"));
    List<ImmutableList<TargetExpression>> shards =
        BlazeBuildTargetSharder.shardTargetsRetainingOrdering(targets, 3);
    assertThat(shards).hasSize(2);
    assertThat(shards.get(0)).hasSize(5);
    assertThat(shards.get(0))
        .isEqualTo(
            ImmutableList.of(
                target("//java/com/google:one"),
                target("-//java/com/google:two"),
                target("//java/com/google:three"),
                target("-//java/com/google:four"),
                target("-//java/com/google:six")));
    assertThat(shards.get(1)).hasSize(3);
    assertThat(shards.get(1))
        .containsExactly(
            target("-//java/com/google:four"),
            target("//java/com/google:five"),
            target("-//java/com/google:six"))
        .inOrder();

    shards = BlazeBuildTargetSharder.shardTargetsRetainingOrdering(targets, 1);
    assertThat(shards).hasSize(3);
    assertThat(shards.get(0))
        .containsExactly(
            target("//java/com/google:one"),
            target("-//java/com/google:two"),
            target("-//java/com/google:four"),
            target("-//java/com/google:six"))
        .inOrder();
  }

  @Test
  public void shardTargetsRetainingOrdering_testShardWithOnlyExcludedTargetsIsDropped() {
    List<TargetExpression> targets =
        ImmutableList.of(
            target("//java/com/google:one"),
            target("//java/com/google:two"),
            target("//java/com/google:three"),
            target("-//java/com/google:four"),
            target("-//java/com/google:five"),
            target("-//java/com/google:six"));

    List<ImmutableList<TargetExpression>> shards =
        BlazeBuildTargetSharder.shardTargetsRetainingOrdering(targets, 3);

    assertThat(shards).hasSize(1);
    assertThat(shards.get(0)).hasSize(6);
  }

  @Test
  public void expandAndShardTargets_shardingApproachNone() {
    mockExperimentService.setExperiment(shardAutomatically, false);
    List<TargetExpression> targets = ImmutableList.of(target("//java/com/google:foo"));
    ShardedTargetsResult result =
        expandAndShardTargets(BuildBinaryType.BLAZE, ProjectView.builder().build(), targets);

    assertThat(result.buildResult.exitCode).isEqualTo(0);
    assertThat(result.shardedTargets.shardStats.shardingApproach())
        .isEqualTo(ShardingApproach.NONE);
  }

  @Test
  public void expandAndShardTargets_shardingApproachPartitionWithoutExpanding() {
    mockExperimentService.setExperiment(shardAutomatically, true);
    List<TargetExpression> targets = ImmutableList.of(target("//java/com/google:foo"));
    ShardedTargetsResult result =
        expandAndShardTargets(BuildBinaryType.BLAZE, ProjectView.builder().build(), targets);

    assertThat(result.buildResult.exitCode).isEqualTo(0);
    assertThat(result.shardedTargets.shardStats.shardingApproach())
        .isEqualTo(ShardingApproach.PARTITION_WITHOUT_EXPANDING);
  }

  @Test
  public void expandAndShardTargets_remoteBuild_buildBatchingServiceIsUsed() {
    mockExperimentService.setExperiment(shardAutomatically, true);
    fakeBuildBatchingService
        .setShardingApproach(ShardingApproach.BUILD_TARGET_BATCHING_SERVICE)
        .setFailToBatchTarget(false);
    mockExperimentService.setExperiment(shardAutomatically, true);
    List<TargetExpression> targets = ImmutableList.of(target("//java/com/google:foo"));
    ShardedTargetsResult result =
        expandAndShardTargets(BuildBinaryType.RABBIT, ProjectView.builder().build(), targets);

    assertThat(result.buildResult.exitCode).isEqualTo(0);
    assertThat(result.shardedTargets.shardStats.shardingApproach())
        .isEqualTo(ShardingApproach.BUILD_TARGET_BATCHING_SERVICE);
  }

  @Test
  public void expandAndShardTargets_localBuild_buildBatchingServiceIsUsed() {
    mockExperimentService.setExperiment(shardAutomatically, true);
    fakeBuildBatchingService
        .setShardingApproach(ShardingApproach.LEXICOGRAPHIC_TARGET_SHARDER)
        .setFailToBatchTarget(false);
    List<TargetExpression> targets = ImmutableList.of(target("//java/com/google:foo"));
    ShardedTargetsResult result =
        expandAndShardTargets(
            BuildBinaryType.BLAZE,
            ProjectView.builder()
                .add(ScalarSection.builder(ShardBlazeBuildsSection.KEY).set(true))
                .add(ScalarSection.builder(TargetShardSizeSection.KEY).set(500))
                .build(),
            targets);

    assertThat(result.buildResult.exitCode).isEqualTo(0);
    ShardStats shardStats = result.shardedTargets.shardStats;
    assertThat(shardStats.shardingApproach())
        .isEqualTo(ShardingApproach.LEXICOGRAPHIC_TARGET_SHARDER);
  }

  @Test
  public void expandAndShardTargets_failToExpand_shardingApproachError() {
    mockExperimentService.setExperiment(shardAutomatically, true);
    fakeWildCardTargetExpanderExternalTaskProvider.setReturnVal(2);
    fakeBuildBatchingService
        .setShardingApproach(ShardingApproach.LEXICOGRAPHIC_TARGET_SHARDER)
        .setFailToBatchTarget(false);
    List<TargetExpression> targets = ImmutableList.of(target("//java/com/google/..."));
    ShardedTargetsResult result =
        expandAndShardTargets(
            BuildBinaryType.RABBIT,
            ProjectView.builder()
                .add(ScalarSection.builder(ShardBlazeBuildsSection.KEY).set(true))
                .add(ScalarSection.builder(TargetShardSizeSection.KEY).set(500))
                .build(),
            targets);

    assertThat(result.buildResult.exitCode).isEqualTo(2);
    assertThat(result.shardedTargets.shardStats.shardingApproach())
        .isEqualTo(ShardingApproach.ERROR);
  }

  @Test
  public void expandAndShardTargets_failToBatchingTargets_shardingApproachError() {
    mockExperimentService.setExperiment(shardAutomatically, true);
    fakeWildCardTargetExpanderExternalTaskProvider.setReturnVal(0);
    fakeBuildBatchingService
        .setShardingApproach(ShardingApproach.LEXICOGRAPHIC_TARGET_SHARDER)
        .setFailToBatchTarget(true);
    List<TargetExpression> targets = ImmutableList.of(target("//java/com/google:foo"));
    ShardedTargetsResult result =
        expandAndShardTargets(
            BuildBinaryType.RABBIT,
            ProjectView.builder()
                .add(ScalarSection.builder(ShardBlazeBuildsSection.KEY).set(true))
                .add(ScalarSection.builder(TargetShardSizeSection.KEY).set(500))
                .build(),
            targets);

    assertThat(result.buildResult.exitCode).isEqualTo(0);
    assertThat(result.shardedTargets.shardStats.shardingApproach())
        .isEqualTo(ShardingApproach.ERROR);
  }

  @Test
  public void expandAndShardTargets_expandWildcardTargets() {
    String expectedLabel1 = "//java/com/google:one";
    String expectedLabel2 = "//java/com/google:two";
    mockExperimentService.setExperiment(shardAutomatically, true);
    fakeWildCardTargetExpanderExternalTaskProvider
        .setReturnVal(0)
        .setOutputMessage("sh_library rule " + expectedLabel1, "sh_library rule " + expectedLabel2);
    fakeBuildBatchingService
        .setShardingApproach(ShardingApproach.LEXICOGRAPHIC_TARGET_SHARDER)
        .setFailToBatchTarget(false);

    List<TargetExpression> targets = ImmutableList.of(target("//java/com/google/..."));
    ShardedTargetsResult result =
        expandAndShardTargets(
            BuildBinaryType.RABBIT,
            ProjectView.builder()
                .add(ScalarSection.builder(ShardBlazeBuildsSection.KEY).set(true))
                .add(ScalarSection.builder(TargetShardSizeSection.KEY).set(500))
                .build(),
            targets);

    ShardStats shardStats = result.shardedTargets.shardStats;
    assertThat(shardStats.suggestedTargetSizePerShard()).isEqualTo(500);
    assertThat(shardStats.actualTargetSizePerShard()).containsExactly(2);
    assertThat(result.shardedTargets.shardedTargets)
        .containsExactly(ImmutableList.of(target(expectedLabel1), target(expectedLabel2)));
  }

  private ShardedTargetsResult expandAndShardTargets(
      BuildBinaryType buildBinaryType, ProjectView projectView, List<TargetExpression> targets) {
    WorkspaceRoot workspaceRoot = new WorkspaceRoot(new File("workspaceRoot"));
    return BlazeBuildTargetSharder.expandAndShardTargets(
        getProject(),
        new BlazeContext(),
        workspaceRoot,
        BlazeBuildParams.builder()
            .setBlazeBinaryPath("foo")
            .setBlazeBinaryType(buildBinaryType)
            .build(),
        ProjectViewSet.builder().add(projectView).build(),
        new WorkspacePathResolverImpl(workspaceRoot),
        targets);
  }

  private static TargetExpression target(String expression) {
    return Preconditions.checkNotNull(TargetExpression.fromStringSafe(expression));
  }

  private static class FakeWildCardTargetExpanderExternalTaskProvider
      implements ExternalTaskProvider {
    String[] outputMessage = new String[0];
    int returnVal = 0;

    public FakeWildCardTargetExpanderExternalTaskProvider setOutputMessage(
        String... outputMessage) {
      this.outputMessage = outputMessage;
      return this;
    }

    public FakeWildCardTargetExpanderExternalTaskProvider setReturnVal(int returnVal) {
      this.returnVal = returnVal;
      return this;
    }

    @Override
    public ExternalTask build(ExternalTask.Builder builder) {
      return new FakeWildCardTargetExpanderExternalTask(
          builder.stdout, builder.stderr, returnVal, outputMessage);
    }
  }

  private static class FakeWildCardTargetExpanderExternalTask implements ExternalTask {
    private static final OutputStream NULL_STREAM = ByteStreams.nullOutputStream();
    final OutputStream stdout;
    final OutputStream stderr;
    final String[] outputMessages;
    final int returnVal;

    FakeWildCardTargetExpanderExternalTask(
        @Nullable OutputStream stdout,
        @Nullable OutputStream stderr,
        int returnVal,
        String... outputMessages) {
      this.stdout = stdout != null ? stdout : NULL_STREAM;
      this.stderr = stderr != null ? stderr : NULL_STREAM;
      this.outputMessages = outputMessages;
      this.returnVal = returnVal;
    }

    @Override
    public int run(BlazeScope... scopes) {
      try {
        for (String outputMessage : outputMessages) {
          this.stdout.write(outputMessage.getBytes(UTF_8));
          this.stdout.write(System.lineSeparator().getBytes(UTF_8));
        }
      } catch (IOException e) {
        fail("Fail to redirect output: " + e.getMessage());
      }
      return returnVal;
    }
  }

  private static class FakeFileOperationProvider extends FileOperationProvider {
    @Override
    public boolean isDirectory(File file) {
      return false;
    }
  }

  private static class FakePrefetchService implements PrefetchService {
    @Override
    public ListenableFuture<?> prefetchFiles(
        Collection<File> files, boolean refetchCachedFiles, boolean fetchFileTypes) {
      return Futures.immediateFuture(null);
    }

    @Override
    public ListenableFuture<?> prefetchProjectFiles(
        Project project,
        ProjectViewSet projectViewSet,
        @Nullable BlazeProjectData blazeProjectData) {
      return Futures.immediateFuture(null);
    }

    @Override
    public void clearPrefetchCache() {}
  }

  private static class FakeBlazeSyncPlugin implements BlazeSyncPlugin {

    @Override
    public WorkspaceType getDefaultWorkspaceType() {
      return WorkspaceType.JAVA;
    }
  }

  private static class FakeBuildBatchingService implements BuildBatchingService {
    private ShardingApproach shardingApproach = ShardingApproach.ERROR;
    private boolean failToBatchTargets = false;

    @Nullable
    @Override
    public ImmutableList<ImmutableList<Label>> calculateTargetBatches(
        Set<Label> targets, BuildBinaryType buildType, int suggestedShardSize) {
      return failToBatchTargets
          ? null
          : ImmutableList.of(targets).stream()
              .map(ImmutableList::copyOf)
              .collect(toImmutableList());
    }

    @Override
    public ShardingApproach getShardingApproach() {
      return shardingApproach;
    }

    public FakeBuildBatchingService setShardingApproach(ShardingApproach shardingApproach) {
      this.shardingApproach = shardingApproach;
      return this;
    }

    public FakeBuildBatchingService setFailToBatchTarget(boolean failToBatchTargets) {
      this.failToBatchTargets = failToBatchTargets;
      return this;
    }
  }
}
