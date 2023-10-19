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
package com.google.idea.blaze.base.settings;

import com.google.common.truth.Truth;
import com.google.idea.blaze.base.BlazeIntegrationTestCase;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.settings.BlazeImportSettings.ProjectType;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Integration tests for {@link Blaze} */
@RunWith(JUnit4.class)
public class BlazeTest extends BlazeIntegrationTestCase {
  @Test
  public void testGetProjectType_importSettingsNotReady_returnUnknown() {
    BlazeImportSettingsManager.getInstance(getProject()).setImportSettings(null);
    Truth.assertThat(Blaze.getProjectType(getProject())).isEqualTo(ProjectType.UNKNOWN);
  }

  @Test
  public void testGetProjectType_querySyncSettings_returnQuerySync() {
    BlazeImportSettingsManager.getInstance(getProject())
        .setImportSettings(
            new BlazeImportSettings(
                workspaceRoot.toString(),
                "test-project",
                projectDataDirectory.getPath(),
                workspaceRoot.fileForPath(new WorkspacePath("project-view-file")).getPath(),
                buildSystem(),
                ProjectType.QUERY_SYNC));
    Truth.assertThat(Blaze.getProjectType(getProject())).isEqualTo(ProjectType.QUERY_SYNC);
  }

  @Test
  public void testGetProjectType_aspectSyncSettings_returnAspectSync() {
    BlazeImportSettingsManager.getInstance(getProject())
        .setImportSettings(
            new BlazeImportSettings(
                workspaceRoot.toString(),
                "test-project",
                projectDataDirectory.getPath(),
                workspaceRoot.fileForPath(new WorkspacePath("project-view-file")).getPath(),
                buildSystem(),
                ProjectType.ASPECT_SYNC));
    Truth.assertThat(Blaze.getProjectType(getProject())).isEqualTo(ProjectType.ASPECT_SYNC);
  }
}
