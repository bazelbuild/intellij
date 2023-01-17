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
package com.google.idea.blaze.plugin.sync;

import static com.google.common.truth.Truth.assertThat;

import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetMap;
import com.google.idea.blaze.base.ideinfo.TargetMapBuilder;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.model.primitives.WorkspaceType;
import com.google.idea.blaze.base.sync.BlazeSyncIntegrationTestCase;
import com.google.idea.blaze.base.sync.BlazeSyncParams;
import com.google.idea.blaze.base.sync.SyncMode;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.plugin.run.BlazeIntellijPluginConfiguration;
import com.intellij.execution.RunManager;
import com.intellij.execution.configurations.RunConfiguration;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Plugin-dev specific sync integration test.
 *
 * Tests additional_languages: scala support in plugin workspace
 */
@RunWith(JUnit4.class)
public class PluginScalaDevSyncTest extends BlazeSyncIntegrationTestCase {

  @Test
  public void testRunConfigurationCreatedDuringSync() throws Exception {
    setProjectView(
        "directories:",
        "  java/com/google",
        "targets:",
        "  //java/com/google:lib",
        "  //java/com/google:plugin",
        "workspace_type: intellij_plugin",
        "additional_languages:",
        "  scala");

    workspace.createFile(
        new WorkspacePath("java/com/google/ClassWithUniqueName1.java"),
        "package com.google;",
        "public class ClassWithUniqueName1 {}");

    workspace.createFile(
        new WorkspacePath("java/com/google/ClassWithUniqueName2.java"),
        "package com.google;",
        "public class ClassWithUniqueName2 {}");

    TargetMap targetMap =
        TargetMapBuilder.builder()
            .addTarget(
                TargetIdeInfo.builder()
                    .setBuildFile(sourceRoot("java/com/google/BUILD"))
                    .setLabel("//java/com/google:lib")
                    .setKind("java_library")
                    .addSource(sourceRoot("java/com/google/ClassWithUniqueName1.java"))
                    .addSource(sourceRoot("java/com/google/ClassWithUniqueName2.java")))
            .addTarget(
                TargetIdeInfo.builder()
                    .setBuildFile(sourceRoot("java/com/google/BUILD"))
                    .setLabel("//java/com/google:plugin")
                    .setKind("intellij_plugin_debug_target"))
            .build();

    setTargetMap(targetMap);

    runBlazeSync(
        BlazeSyncParams.builder()
            .setTitle("Sync")
            .setSyncOrigin("test")
            .setSyncMode(SyncMode.INCREMENTAL)
            .setAddProjectViewTargets(true)
            .build());

    errorCollector.assertNoIssues();

    BlazeProjectData blazeProjectData =
        BlazeProjectDataManager.getInstance(getProject()).getBlazeProjectData();
    assertThat(blazeProjectData).isNotNull();
    assertThat(blazeProjectData.getTargetMap()).isEqualTo(targetMap);
    assertThat(blazeProjectData.getWorkspaceLanguageSettings().getWorkspaceType())
        .isEqualTo(WorkspaceType.INTELLIJ_PLUGIN);

    List<RunConfiguration> runConfigs =
        RunManager.getInstance(getProject()).getAllConfigurationsList();
    assertThat(runConfigs).hasSize(1);
    assertThat(runConfigs.get(0)).isInstanceOf(BlazeIntellijPluginConfiguration.class);
  }
}
