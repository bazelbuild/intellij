/*
 * Copyright 2018 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.java.run;

import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.run.testlogs.BlazeTestResult;
import com.google.idea.blaze.base.run.testlogs.BlazeTestResult.TestStatus;
import com.google.idea.blaze.base.run.testlogs.BlazeTestResultFinderStrategy;
import com.google.idea.blaze.base.run.testlogs.BlazeTestResults;
import com.google.idea.blaze.java.run.FastBuildConfigurationRunner.FastBuildLoggingData;
import java.io.File;
import java.util.concurrent.TimeUnit;

final class FastBuildTestResultFinderStrategy implements BlazeTestResultFinderStrategy {

  private final Label label;
  private final Kind kind;
  private final File outputFile;
  private final FastBuildLoggingData loggingData;
  private final Stopwatch timer;

  FastBuildTestResultFinderStrategy(
      Label label, Kind kind, File outputFile, FastBuildLoggingData loggingData) {
    this.label = label;
    this.kind = kind;
    this.outputFile = outputFile;
    this.loggingData = loggingData;
    this.timer = Stopwatch.createStarted();
  }

  @Override
  public BlazeTestResults findTestResults() {
    loggingData.put("run_test_class_time_ms", Long.toString(timer.elapsed(TimeUnit.MILLISECONDS)));
    loggingData.writeLog();
    return BlazeTestResults.fromFlatList(
        ImmutableList.of(
            BlazeTestResult.create(
                label, kind, TestStatus.NO_STATUS, ImmutableSet.of(outputFile))));
  }

  @Override
  public void deleteTemporaryOutputXmlFiles() {
    outputFile.delete();
  }
}
