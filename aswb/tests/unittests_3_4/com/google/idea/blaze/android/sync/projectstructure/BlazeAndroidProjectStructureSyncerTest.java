/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.android.sync.projectstructure;

import static com.google.common.truth.Truth.assertThat;

import com.google.idea.blaze.base.projectview.ProjectView;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.projectview.section.ListSection;
import com.google.idea.blaze.base.projectview.section.sections.BuildFlagsSection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link BlazeAndroidProjectStructureSyncer}. */
@RunWith(JUnit4.class)
public class BlazeAndroidProjectStructureSyncerTest {
  @Test
  public void hasConfigAndroidJava8Libs_emptyBuildFlags() {
    ProjectViewSet set = ProjectViewSet.builder().build();
    assertThat(BlazeAndroidProjectStructureSyncer.hasConfigAndroidJava8Libs(set)).isFalse();
  }

  @Test
  public void hasConfigAndroidJava8Libs_validConfig() {
    ProjectViewSet set =
        ProjectViewSet.builder()
            .add(
                ProjectView.builder()
                    .add(
                        ListSection.builder(BuildFlagsSection.KEY)
                            .add("--config=android_java8_libs"))
                    .build())
            .build();
    assertThat(BlazeAndroidProjectStructureSyncer.hasConfigAndroidJava8Libs(set)).isTrue();
  }
}
