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
package com.google.idea.blaze.kotlin;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.base.ideinfo.JavaIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetMap;
import com.google.idea.blaze.base.ideinfo.TargetMapBuilder;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.blaze.base.model.primitives.WorkspaceType;
import com.google.idea.blaze.base.sync.BlazeSyncIntegrationTestCase;
import com.google.idea.blaze.base.sync.BlazeSyncParams;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.base.sync.projectview.WorkspaceLanguageSettings;
import com.google.idea.blaze.java.sync.model.BlazeContentEntry;
import com.google.idea.blaze.java.sync.model.BlazeJavaSyncData;
import com.google.idea.blaze.java.sync.model.BlazeSourceDirectory;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Kotlin-specific sync integration tests. */
@RunWith(JUnit4.class)
public class KotlinSyncTest extends BlazeSyncIntegrationTestCase {
  @Test
  public void testKotlinClassesPresentInClassPath() {
    setProjectView(
        "directories:",
        "  src/main/kotlin/com/google",
        "targets:",
        "  //src/main/kotlin/com/google:lib",
        "additional_languages:",
        "  kotlin");

    workspace.createFile(
        new WorkspacePath("src/main/kotlin/com/google/ClassWithUniqueName1.kt"),
        "package com.google;",
        "public class ClassWithUniqueName1 {}");

    workspace.createFile(
        new WorkspacePath("src/main/kotlin/com/google/ClassWithUniqueName2.kt"),
        "package com.google;",
        "public class ClassWithUniqueName2 {}");
    workspace.createDirectory(new WorkspacePath("external/com_github_jetbrains_kotlin"));

    TargetMap targetMap =
        TargetMapBuilder.builder()
            .addTarget(
                TargetIdeInfo.builder()
                    .setBuildFile(sourceRoot("src/main/kotlin/com/google/BUILD"))
                    .setLabel("//src/main/kotlin/com/google:lib")
                    .setKind("kt_jvm_library")
                    .addSource(sourceRoot("src/main/kotlin/com/google/ClassWithUniqueName1.scala"))
                    .addSource(sourceRoot("src/main/kotlin/com/google/ClassWithUniqueName2.scala"))
                    .setJavaInfo(JavaIdeInfo.builder()))
            .build();

    setTargetMap(targetMap);

    BlazeSyncParams syncParams =
        new BlazeSyncParams.Builder("Full Sync", BlazeSyncParams.SyncMode.FULL)
            .addProjectViewTargets(true)
            .build();
    runBlazeSync(syncParams);

    BlazeProjectData blazeProjectData =
        BlazeProjectDataManager.getInstance(getProject()).getBlazeProjectData();
    assertThat(blazeProjectData).isNotNull();
    assertThat(blazeProjectData.targetMap).isEqualTo(targetMap);
    assertThat(blazeProjectData.workspaceLanguageSettings)
        .isEqualTo(
            new WorkspaceLanguageSettings(
                WorkspaceType.JAVA,
                ImmutableSet.of(LanguageClass.GENERIC, LanguageClass.JAVA, LanguageClass.KOTLIN)));

    BlazeJavaSyncData javaSyncData = blazeProjectData.syncState.get(BlazeJavaSyncData.class);
    assertThat(javaSyncData).isNotNull();
    List<BlazeContentEntry> contentEntries = javaSyncData.importResult.contentEntries;
    assertThat(contentEntries).hasSize(1);

    BlazeContentEntry contentEntry = contentEntries.get(0);
    assertThat(contentEntry.contentRoot.getPath())
        .isEqualTo(
            this.workspaceRoot
                .fileForPath(new WorkspacePath("src/main/kotlin/com/google"))
                .getPath());
    assertThat(contentEntry.sources).hasSize(1);

    BlazeSourceDirectory sourceDir = contentEntry.sources.get(0);
    assertThat(sourceDir.getPackagePrefix()).isEqualTo("com.google");
    assertThat(sourceDir.getDirectory().getPath())
        .isEqualTo(
            this.workspaceRoot
                .fileForPath(new WorkspacePath("src/main/kotlin/com/google"))
                .getPath());
  }

  @Test
  public void testSimpleSync() {
    setProjectView(
        "directories:",
        "  src/main/kotlin/com/google",
        "targets:",
        "  //src/main/kotlin/com/google:lib",
        "additional_languages:",
        "  kotlin");

    workspace.createFile(
        new WorkspacePath("src/main/kotlin/com/google/Source.kt"),
        "package com.google;",
        "public class Source {}");

    workspace.createFile(
        new WorkspacePath("src/main/kotlin/com/google/Other.kt"),
        "package com.google;",
        "public class Other {}");
    workspace.createDirectory(new WorkspacePath("external/com_github_jetbrains_kotlin"));

    TargetMap targetMap =
        TargetMapBuilder.builder()
            .addTarget(
                TargetIdeInfo.builder()
                    .setBuildFile(sourceRoot("src/main/kotlin/com/google/BUILD"))
                    .setLabel("//src/main/kotlin/com/google:lib")
                    .setKind("kt_jvm_library")
                    .addSource(sourceRoot("src/main/kotlin/com/google/Source.kotlin"))
                    .addSource(sourceRoot("src/main/kotlin/com/google/Other.kotlin"))
                    .setJavaInfo(JavaIdeInfo.builder()))
            .build();

    setTargetMap(targetMap);

    runBlazeSync(
        new BlazeSyncParams.Builder("Sync", BlazeSyncParams.SyncMode.INCREMENTAL)
            .addProjectViewTargets(true)
            .build());

    BlazeProjectData blazeProjectData =
        BlazeProjectDataManager.getInstance(getProject()).getBlazeProjectData();
    assertThat(blazeProjectData).isNotNull();
    assertThat(blazeProjectData.targetMap).isEqualTo(targetMap);
    assertThat(blazeProjectData.workspaceLanguageSettings)
        .isEqualTo(
            new WorkspaceLanguageSettings(
                WorkspaceType.JAVA,
                ImmutableSet.of(LanguageClass.GENERIC, LanguageClass.KOTLIN, LanguageClass.JAVA)));
  }
}
