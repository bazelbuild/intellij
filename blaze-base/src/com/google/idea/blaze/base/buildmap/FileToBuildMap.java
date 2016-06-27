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
package com.google.idea.blaze.base.buildmap;

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.rulemaps.SourceToRuleMap;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;

import java.io.File;
import java.util.Collection;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Map of file -> BUILD files.
 */
public class FileToBuildMap {
  private final Project project;

  public static FileToBuildMap getInstance(Project project) {
    return ServiceManager.getService(project, FileToBuildMap.class);
  }

  public FileToBuildMap(Project project) {
    this.project = project;
  }

  public Collection<File> getBuildFilesForFile(File file) {
    BlazeProjectData blazeProjectData = BlazeProjectDataManager.getInstance(project).getBlazeProjectData();
    if (blazeProjectData == null) {
      return ImmutableList.of();
    }
    return SourceToRuleMap.getInstance(project).getTargetsForSourceFile(file)
      .stream()
      .map(blazeProjectData.ruleMap::get)
      .filter(Objects::nonNull)
      .map((ruleIdeInfo) -> ruleIdeInfo.buildFile)
      .filter(Objects::nonNull)
      .map(ArtifactLocation::getFile)
      .collect(Collectors.toList());
  }
}
