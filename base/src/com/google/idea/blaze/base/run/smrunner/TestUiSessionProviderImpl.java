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
package com.google.idea.blaze.base.run.smrunner;

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.command.buildresult.BuildEventProtocolUtils;
import com.google.idea.blaze.base.command.buildresult.BuildEventStreamProvider;
import com.google.idea.blaze.base.command.buildresult.BuildResultHelper;
import com.google.idea.blaze.base.model.primitives.TargetExpression;
import com.google.idea.blaze.base.run.testlogs.BuildEventProtocolLocalTestFinderStrategy;
import com.google.idea.blaze.base.run.testlogs.BuildEventProtocolRemoteTestFinderStrategy;
import com.intellij.openapi.project.Project;
import java.io.File;
import javax.annotation.Nullable;

/** Provides a {@link BlazeTestUiSession} for Bazel projects. */
public class TestUiSessionProviderImpl implements TestUiSessionProvider {

  private final Project project;

  public TestUiSessionProviderImpl(Project project) {
    this.project = project;
  }

  @Nullable
  @Override
  public BlazeTestUiSession getTestUiSession(
      ImmutableList<? extends TargetExpression> targets, BuildResultHelper buildResultHelper) {
    if (!BlazeTestEventsHandler.targetsSupported(project, targets)) {
      return null;
    }
    ImmutableList<String> flags = ImmutableList.of("--runs_per_test=1", "--flaky_test_attempts=1");
    if (buildResultHelper.areBuildResultsRemote()) {
      BuildEventStreamProvider streamProvider =
          buildResultHelper.getBuildEventStream().orElseThrow();
      return BlazeTestUiSession.create(
          flags, new BuildEventProtocolRemoteTestFinderStrategy(streamProvider));
    } else {
      File bepOutputFile = BuildEventProtocolUtils.createTempOutputFile();
      return BlazeTestUiSession.create(
          ImmutableList.<String>builder()
              .addAll(flags)
              .addAll(BuildEventProtocolUtils.getBuildFlags(bepOutputFile))
              .build(),
          new BuildEventProtocolLocalTestFinderStrategy(bepOutputFile));
    }
  }
}
