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
package com.google.idea.blaze.base.wizard;

import com.google.idea.blaze.base.projectview.ProjectView;
import com.google.idea.blaze.base.settings.BlazeImportSettings;

import javax.annotation.concurrent.Immutable;

@Immutable
public final class ImportResults {

  public final BlazeImportSettings importSettings;
  public final ProjectView projectView;
  public final String projectName;
  public final String projectDataDirectory;

  public ImportResults(
    BlazeImportSettings importSettings,
    ProjectView projectView,
    String projectName,
    String projectDataDirectory
  ) {
    this.importSettings = importSettings;
    this.projectView = projectView;
    this.projectName = projectName;
    this.projectDataDirectory = projectDataDirectory;
  }
}
