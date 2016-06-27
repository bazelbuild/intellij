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

import com.google.common.collect.ImmutableMap;
import com.google.idea.blaze.base.ideinfo.RuleIdeInfo;
import com.google.idea.blaze.base.ideinfo.RuleMapBuilder;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.WorkspaceType;
import com.google.idea.blaze.base.sync.BlazeSyncIntegrationTestCase;
import com.google.idea.blaze.base.sync.actions.IncrementalSyncProjectAction;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.plugin.run.BlazeIntellijPluginConfiguration;
import com.intellij.execution.RunManager;
import com.intellij.execution.configurations.RunConfiguration;

import java.util.List;

import static com.google.common.truth.Truth.assertThat;

/**
 * Plugin-dev specific sync integration test.
 */
public class PluginDevSyncTest extends BlazeSyncIntegrationTestCase {
  
  public void ignore_testRunConfigurationCreatedDuringSync() throws Exception {
    setProjectView(
      "directories:",
      "  java/com/google",
      "targets:",
      "  //java/com/google:lib",
      "  //java/com/google:plugin",
      "workspace_type: intellij_plugin"
    );

    createFile(
      "java/com/google/ClassWithUniqueName1.java",
      "package com.google;",
      "public class ClassWithUniqueName1 {}"
    );

    createFile(
      "java/com/google/ClassWithUniqueName2.java",
      "package com.google;",
      "public class ClassWithUniqueName2 {}"
    );

    ImmutableMap<Label, RuleIdeInfo> ruleMap = RuleMapBuilder.builder()
      .addRule(RuleIdeInfo.builder()
                 .setBuildFile(sourceRoot("java/com/google/BUILD"))
                 .setLabel("//java/com/google:lib")
                 .setKind("java_library")
                 .addSource(sourceRoot("java/com/google/ClassWithUniqueName1.java"))
                 .addSource(sourceRoot("java/com/google/ClassWithUniqueName2.java")))
      .addRule(RuleIdeInfo.builder()
                 .setBuildFile(sourceRoot("java/com/google/BUILD"))
                 .setLabel("//java/com/google:plugin")
                 .setKind("java_import")
                 .addTag("intellij-plugin")
      )
      .build();

    setRuleMap(ruleMap);

    runBlazeSync(IncrementalSyncProjectAction.manualSyncParams);

    assertNoErrors();

    BlazeProjectData blazeProjectData = BlazeProjectDataManager.getInstance(getProject()).getBlazeProjectData();
    assertThat(blazeProjectData).isNotNull();
    assertThat(blazeProjectData.ruleMap).isEqualTo(ruleMap);
    assertThat(blazeProjectData.workspaceLanguageSettings.getWorkspaceType())
      .isEqualTo(WorkspaceType.INTELLIJ_PLUGIN);

    List<RunConfiguration> runConfigs = RunManager.getInstance(getProject()).getAllConfigurationsList();
    assertThat(runConfigs).hasSize(1);
    assertThat(runConfigs.get(0)).isInstanceOf(BlazeIntellijPluginConfiguration.class);
  }

}
