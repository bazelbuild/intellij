/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.cpp;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.BlazeTestCase;
import com.google.idea.sdkcompat.cidr.CPPEnvironmentAdapter;
import com.google.idea.sdkcompat.cidr.CidrCompilerSwitchesAdapter;
import com.jetbrains.cidr.lang.OCLanguageKind;
import com.jetbrains.cidr.lang.toolchains.CidrCompilerSwitches;
import com.jetbrains.cidr.toolchains.CompilerInfoCache;
import java.io.File;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link BlazeCompilerSettings}. */
@RunWith(JUnit4.class)
public class BlazeCompilerSettingsTest extends BlazeTestCase {

  @Override
  protected void initTest(Container applicationServices, Container projectServices) {
    CPPEnvironmentAdapter.registerForTest(applicationServices.getPicoContainer());
  }

  @Test
  public void testCompilerSwitchesSimple() {
    File cppExe = new File("bin/cpp");
    ImmutableList<String> cFlags = ImmutableList.of("-fast", "-slow");
    CompilerInfoCache compilerInfoCache = new CompilerInfoCache();
    BlazeCompilerSettings settings =
        new BlazeCompilerSettings(
            getProject(),
            cppExe,
            cppExe,
            cFlags,
            cFlags,
            "cc version (trunk r123456)",
            compilerInfoCache);

    CidrCompilerSwitches compilerSwitches = settings.getCompilerSwitches(OCLanguageKind.C, null);
    List<String> commandLineArgs = CidrCompilerSwitchesAdapter.getFileArgs(compilerSwitches);
    assertThat(commandLineArgs).containsExactly("-fast", "-slow");
  }
}
