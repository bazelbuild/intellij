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
package com.google.idea.blaze.base.sync.aspects;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.idea.blaze.base.BlazeTestCase;
import org.junit.Test;

import java.util.List;

import static com.google.common.truth.Truth.assertThat;

public class UnfilteredCompilerOptionsTest extends BlazeTestCase {
  @Test
  public void testUnfilteredOptionsParsingForISystemOptions() {
    ImmutableList<String> unfilteredOptions = ImmutableList.of(
      "-isystem",
      "sys/inc1",
      "-VER2",
      "-isystem",
      "sys2/inc1",
      "-isystem",
      "sys3/inc1",
      "-isystm",
      "sys4/inc1"
    );
    List<String> sysIncludes = Lists.newArrayList();
    List<String> flags = Lists.newArrayList();
    UnfilteredCompilerOptions.splitUnfilteredCompilerOptions(unfilteredOptions, sysIncludes, flags);

    assertThat(sysIncludes).containsExactly(
      "sys/inc1",
      "sys2/inc1",
      "sys3/inc1"
    );

    assertThat(flags).containsExactly(
      "-VER2",
      "-isystm",
      "sys4/inc1"
    );
  }

  @Test
  public void testUnfilteredOptionsParsingForISystemOptionsNoSpaceAfterIsystem() {
    ImmutableList<String> unfilteredOptions = ImmutableList.of(
      "-isystem",
      "sys/inc1",
      "-VER2",
      "-isystemsys2/inc1",
      "-isystem",
      "sys3/inc1"
    );
    List<String> sysIncludes = Lists.newArrayList();
    List<String> flags = Lists.newArrayList();
    UnfilteredCompilerOptions.splitUnfilteredCompilerOptions(unfilteredOptions, sysIncludes, flags);

    assertThat(sysIncludes).containsExactly(
      "sys/inc1",
      "sys2/inc1",
      "sys3/inc1"
    );

    assertThat(flags).containsExactly("-VER2");
  }
}
