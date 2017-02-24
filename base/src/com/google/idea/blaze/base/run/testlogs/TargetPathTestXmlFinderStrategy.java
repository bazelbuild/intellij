/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.base.run.testlogs;

import com.google.common.collect.ImmutableMultimap;
import com.google.idea.blaze.base.command.info.BlazeInfo;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.Blaze.BuildSystem;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.intellij.openapi.project.Project;
import java.io.File;
import javax.annotation.Nullable;

/**
 * Attempts to parse the list of test targets from the command log, then searches the corresponding
 * path in the bazel-testlogs output tree.
 */
public class TargetPathTestXmlFinderStrategy implements BlazeTestXmlFinderStrategy {

  @Override
  public boolean handlesBuildSystem(BuildSystem buildSystem) {
    return buildSystem == BuildSystem.Bazel;
  }

  @Override
  public ImmutableMultimap<Label, File> findTestXmlFiles(Project project) {
    File testLogsDir = getTestLogsTree(project);
    if (testLogsDir == null) {
      return ImmutableMultimap.of();
    }
    File commandLog = getCommandLog(project);
    if (commandLog == null) {
      return ImmutableMultimap.of();
    }
    ImmutableMultimap.Builder<Label, File> output = ImmutableMultimap.builder();
    for (Label label : BlazeCommandLogParser.parseTestTargets(commandLog)) {
      File testXml = findTestXml(testLogsDir, label);
      if (testXml != null) {
        output.put(label, testXml);
      }
    }
    return output.build();
  }

  @Nullable
  private static File findTestXml(File testLogsDir, Label label) {
    String labelPath = label.blazePackage() + File.separator + label.targetName();
    return new File(testLogsDir, labelPath + File.separator + "test.xml");
  }

  @Nullable
  private static File getTestLogsTree(Project project) {
    BlazeProjectData projectData =
        BlazeProjectDataManager.getInstance(project).getBlazeProjectData();
    if (projectData == null) {
      return null;
    }
    String testLogsLocation =
        projectData.blazeInfo.get(BlazeInfo.blazeTestlogsKey(Blaze.getBuildSystem(project)));
    return testLogsLocation != null ? new File(testLogsLocation) : null;
  }

  @Nullable
  private static File getCommandLog(Project project) {
    BlazeProjectData projectData =
        BlazeProjectDataManager.getInstance(project).getBlazeProjectData();
    if (projectData == null) {
      return null;
    }
    String commandLogLocation = projectData.blazeInfo.get(BlazeInfo.COMMAND_LOG);
    return commandLogLocation != null ? new File(commandLogLocation) : null;
  }
}
