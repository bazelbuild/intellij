/*
 * Copyright 2019 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.java.run.fastbuild;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link FastBuildTestEnvironmentCreator}. */
@RunWith(JUnit4.class)
public final class FastBuildTestEnvironmentCreatorTest {

  @Test
  public void testFindsJvmOpts() {
    JavaCommandBuilder javaCommand = new JavaCommandBuilder();
    FastBuildTestEnvironmentCreator.addJvmOptsFromBlazeFlags(
        ImmutableList.of(
            "--foo",
            "--jvmopt=-Dmy.test.property=true",
            "--foo3",
            "--jvmopt",
            "-Xmx10111m",
            "--barz"),
        javaCommand);
    assertThat(javaCommand.getJvmArgs()).containsExactly("-Dmy.test.property=true", "-Xmx10111m");
  }

  @Test
  public void ignoresTrailingJvmOptWithNoArg() {
    JavaCommandBuilder javaCommand = new JavaCommandBuilder();
    FastBuildTestEnvironmentCreator.addJvmOptsFromBlazeFlags(
        ImmutableList.of("--jvmopt=-Dmy.test.property=true", "--jvmopt"), javaCommand);
    assertThat(javaCommand.getJvmArgs()).containsExactly("-Dmy.test.property=true");
  }

  @Test
  public void doesNotAlterArgs() {
    JavaCommandBuilder javaCommand = new JavaCommandBuilder();
    FastBuildTestEnvironmentCreator.addJvmOptsFromBlazeFlags(
        ImmutableList.of("--jvmopt=-Dpunctutation.\"'%$.ignored=yep!"), javaCommand);
    assertThat(javaCommand.getJvmArgs()).containsExactly("-Dpunctutation.\"'%$.ignored=yep!");
  }
}
