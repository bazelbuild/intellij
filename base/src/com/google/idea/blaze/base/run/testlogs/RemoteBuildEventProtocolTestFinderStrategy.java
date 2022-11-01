/*
 * Copyright 2022 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.run.testlogs;

import com.google.idea.blaze.base.command.buildresult.BuildEventProtocolOutputReader;
import com.google.idea.blaze.base.command.buildresult.BuildEventStreamProvider;
import com.google.idea.blaze.base.command.buildresult.BuildEventStreamProvider.BuildEventStreamException;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.Nullable;

/**
 * A strategy for locating results from a single 'blaze test' invocation (e.g. output XML files).
 *
 * <p>Parses the output BEP proto from the BuildEventStream to locate the test XML files.
 */
public class RemoteBuildEventProtocolTestFinderStrategy implements BlazeTestResultFinderStrategy {
  private static final Logger logger =
      Logger.getInstance(RemoteBuildEventProtocolTestFinderStrategy.class);
  private final BuildEventStreamProvider streamProvider;

  public RemoteBuildEventProtocolTestFinderStrategy(BuildEventStreamProvider streamProvider) {
    this.streamProvider = streamProvider;
  }

  @Nullable
  @Override
  public BlazeTestResults findTestResults() {
    try {
      return BuildEventProtocolOutputReader.parseTestResults(streamProvider);
    } catch (BuildEventStreamException e) {
      logger.warn(e.getMessage());
      return BlazeTestResults.NO_RESULTS;
    }
  }

  @Override
  public void deleteTemporaryOutputFiles() {}
}
