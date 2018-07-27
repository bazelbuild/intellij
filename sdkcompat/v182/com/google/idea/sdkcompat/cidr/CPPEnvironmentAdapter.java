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
package com.google.idea.sdkcompat.cidr;

import com.jetbrains.cidr.cpp.toolchains.CPPEnvironment;
import com.jetbrains.cidr.cpp.toolchains.CPPToolchains;
import com.jetbrains.cidr.cpp.toolchains.CPPToolchains.Toolchain;
import com.jetbrains.cidr.toolchains.OSType;
import org.picocontainer.MutablePicoContainer;

/** Adapter to bridge different SDK versions. */
public class CPPEnvironmentAdapter extends CPPEnvironment {
  public CPPEnvironmentAdapter() {
    super(getOrCreateToolchain());
  }

  /**
   * Needed because CPPToolchains wasn't required prior to 2017.3. Since CPPToolchains moved
   * packages in 2017.3, we need an sdkcompat to register it properly. (But we just won't bother to
   * register it in previous versions, since it isn't needed.) This can be removed after #api172.
   */
  public static void registerForTest(MutablePicoContainer applicationContainer) {
    applicationContainer.registerComponentInstance(
        CPPToolchains.class.getName(), new CPPToolchains());
  }

  private static Toolchain getOrCreateToolchain() {
    Toolchain toolchain = CPPToolchains.getInstance().getDefaultToolchain();
    if (toolchain == null) {
      toolchain = createDefaultToolchain();
    }
    return toolchain;
  }

  private static Toolchain createDefaultToolchain() {
    Toolchain toolchain = new Toolchain(OSType.getCurrent());
    toolchain.setName(Toolchain.DEFAULT);
    return toolchain;
  }
}
