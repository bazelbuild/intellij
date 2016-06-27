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
package com.google.idea.blaze.base.sync;

import com.google.common.collect.ImmutableMap;
import com.google.idea.blaze.base.ideinfo.RuleIdeInfo;
import com.google.idea.blaze.base.ideinfo.RuleMapBuilder;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.WorkspaceType;
import com.google.idea.blaze.base.sync.actions.IncrementalSyncProjectAction;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;

import static com.google.common.truth.Truth.assertThat;

/**
 * Tests for the blaze sync process {@link BlazeSyncTask}
 */
public class BlazeSyncTest extends BlazeSyncIntegrationTestCase {

  public void testSimpleSync() throws Exception {
    setProjectView(
      "directories:",
      "  java/com/google",
      "targets:",
      "  //java/com/google:lib",
      "workspace_type: java"
    );

    createFile(
      "java/com/google/Source.java",
      "package com.google;",
      "public class Source {}"
    );

    createFile(
      "java/com/google/Other.java",
      "package com.google;",
      "public class Other {}"
    );

    ImmutableMap<Label, RuleIdeInfo> ruleMap = RuleMapBuilder.builder()
      .addRule(RuleIdeInfo.builder()
                 .setBuildFile(sourceRoot("java/com/google/BUILD"))
                 .setLabel("//java/com/google:lib")
                 .setKind("java_library")
                 .addSource(sourceRoot("java/com/google/Source.java"))
                 .addSource(sourceRoot("java/com/google/Other.java")))
      .build();

    setRuleMap(ruleMap);

    runBlazeSync(IncrementalSyncProjectAction.manualSyncParams);

    assertNoErrors();

    BlazeProjectData blazeProjectData = BlazeProjectDataManager.getInstance(getProject()).getBlazeProjectData();
    assertThat(blazeProjectData).isNotNull();
    assertThat(blazeProjectData.ruleMap).isEqualTo(ruleMap);
    assertThat(blazeProjectData.workspaceLanguageSettings.getWorkspaceType())
      .isEqualTo(WorkspaceType.JAVA);
  }

}
