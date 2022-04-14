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
package com.google.idea.blaze.base.toolwindow;

import static com.google.common.truth.Truth8.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.BlazeIntegrationTestCase;
import java.time.Instant;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link TasksToolWindowService} */
@RunWith(JUnit4.class)
public class TasksToolWindowServiceTest extends BlazeIntegrationTestCase {
  private static final Instant NOW_INSTANT = Instant.ofEpochSecond(333);

  private Task task;

  @Before
  public void setupService() {
    TasksToolWindowService.getInstance(getProject()).setTimeSource(() -> NOW_INSTANT);
    task = new Task(getProject(), "Test task", Task.Type.SYNC);
  }

  @After
  public void restoreService() throws Exception {
    TasksToolWindowService.getInstance(getProject()).setTimeSource(null);
  }

  @Test
  public void testStartTask() {
    TasksToolWindowService.getInstance(getProject()).startTask(task, ImmutableList.of());
    assertThat(task.getStartTime()).hasValue(NOW_INSTANT);
  }

  @Test
  public void testFinishTask() {
    TasksToolWindowService.getInstance(getProject()).finishTask(task, false, false);
    assertThat(task.getEndTime()).hasValue(NOW_INSTANT);
  }
}
