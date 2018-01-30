/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
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

import com.jetbrains.cidr.lang.toolchains.DefaultCidrToolEnvironment;
import org.picocontainer.MutablePicoContainer;

/** Adapter to bridge different SDK versions. */
public class CPPEnvironmentAdapter extends DefaultCidrToolEnvironment {
  /**
   * Needed because CPPToolchains wasn't required prior to 2017.3. Since CPPToolchains moved
   * packages in 2017.3, we need an sdkcompat to register it properly. (But we just won't bother to
   * register it in previous versions, since it isn't needed.) This can be removed after #api172.
   */
  public static void registerForTest(MutablePicoContainer applicationContainer) {}
}
