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
package com.google.idea.blaze.java.sync;

import static com.google.common.truth.Truth.assertThat;

import com.google.idea.blaze.base.ideinfo.JavaRuleIdeInfo;
import com.google.idea.blaze.base.ideinfo.RuleIdeInfo;
import com.google.idea.blaze.base.ideinfo.RuleMapBuilder;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.RuleMap;
import com.google.idea.blaze.base.model.primitives.WorkspaceType;
import com.google.idea.blaze.base.sync.BlazeSyncIntegrationTestCase;
import com.google.idea.blaze.base.sync.BlazeSyncParams;
import com.google.idea.blaze.base.sync.BlazeSyncParams.SyncMode;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.java.sync.model.BlazeContentEntry;
import com.google.idea.blaze.java.sync.model.BlazeJavaSyncData;
import com.google.idea.blaze.java.sync.model.BlazeSourceDirectory;
import java.util.List;

/** Java-specific sync integration tests. */
public class JavaSyncTest extends BlazeSyncIntegrationTestCase {

  public void testJavaClassesPresentInClassPath() throws Exception {
    setProjectView(
        "directories:",
        "  java/com/google",
        "targets:",
        "  //java/com/google:lib",
        "workspace_type: java");

    createWorkspaceFile(
        "java/com/google/ClassWithUniqueName1.java",
        "package com.google;",
        "public class ClassWithUniqueName1 {}");

    createWorkspaceFile(
        "java/com/google/ClassWithUniqueName2.java",
        "package com.google;",
        "public class ClassWithUniqueName2 {}");

    RuleMap ruleMap =
        RuleMapBuilder.builder()
            .addRule(
                RuleIdeInfo.builder()
                    .setBuildFile(sourceRoot("java/com/google/BUILD"))
                    .setLabel("//java/com/google:lib")
                    .setKind("java_library")
                    .addSource(sourceRoot("java/com/google/ClassWithUniqueName1.java"))
                    .addSource(sourceRoot("java/com/google/ClassWithUniqueName2.java"))
                    .setJavaInfo(JavaRuleIdeInfo.builder()))
            .build();

    setRuleMap(ruleMap);

    BlazeSyncParams syncParams =
        new BlazeSyncParams.Builder("Full Sync", BlazeSyncParams.SyncMode.FULL)
            .addProjectViewTargets(true)
            .build();
    runBlazeSync(syncParams);

    assertNoErrors();

    BlazeProjectData blazeProjectData =
        BlazeProjectDataManager.getInstance(getProject()).getBlazeProjectData();
    assertThat(blazeProjectData).isNotNull();
    assertThat(blazeProjectData.ruleMap).isEqualTo(ruleMap);
    assertThat(blazeProjectData.workspaceLanguageSettings.getWorkspaceType())
        .isEqualTo(WorkspaceType.JAVA);

    BlazeJavaSyncData javaSyncData = blazeProjectData.syncState.get(BlazeJavaSyncData.class);
    List<BlazeContentEntry> contentEntries = javaSyncData.importResult.contentEntries;
    assertThat(contentEntries).hasSize(1);

    BlazeContentEntry contentEntry = contentEntries.get(0);
    assertThat(contentEntry.contentRoot.getPath())
        .isEqualTo(tempDirectory.getPath() + "/java/com/google");
    assertThat(contentEntry.sources).hasSize(1);

    BlazeSourceDirectory sourceDir = contentEntry.sources.get(0);
    assertThat(sourceDir.getPackagePrefix()).isEqualTo("com.google");
    assertThat(sourceDir.getDirectory().getPath())
        .isEqualTo(tempDirectory.getPath() + "/java/com/google");
  }

  public void testSimpleSync() throws Exception {
    setProjectView(
        "directories:",
        "  java/com/google",
        "targets:",
        "  //java/com/google:lib",
        "workspace_type: java");

    createFile("java/com/google/Source.java", "package com.google;", "public class Source {}");

    createFile("java/com/google/Other.java", "package com.google;", "public class Other {}");

    RuleMap ruleMap =
        RuleMapBuilder.builder()
            .addRule(
                RuleIdeInfo.builder()
                    .setBuildFile(sourceRoot("java/com/google/BUILD"))
                    .setLabel("//java/com/google:lib")
                    .setKind("java_library")
                    .addSource(sourceRoot("java/com/google/Source.java"))
                    .addSource(sourceRoot("java/com/google/Other.java")))
            .build();

    setRuleMap(ruleMap);

    runBlazeSync(
        new BlazeSyncParams.Builder("Sync", SyncMode.INCREMENTAL)
            .addProjectViewTargets(true)
            .build());

    assertNoErrors();

    BlazeProjectData blazeProjectData =
        BlazeProjectDataManager.getInstance(getProject()).getBlazeProjectData();
    assertThat(blazeProjectData).isNotNull();
    assertThat(blazeProjectData.ruleMap).isEqualTo(ruleMap);
    assertThat(blazeProjectData.workspaceLanguageSettings.getWorkspaceType())
        .isEqualTo(WorkspaceType.JAVA);
  }
}
