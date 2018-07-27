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

import com.jetbrains.cidr.lang.toolchains.CidrCompilerSwitches;
import java.util.List;

/** Adapter to bridge different SDK versions. */
public class CidrCompilerSwitchesAdapter {
  /** Old interface does not know anything about CidrCompilerSwitches.Format */
  public static List<String> getFileArgs(CidrCompilerSwitches switches) {
    return switches.getList(CidrCompilerSwitches.Format.RAW);
  }

  public static List<String> getCommandLineArgs(CidrCompilerSwitches switches) {
    return switches.getList(CidrCompilerSwitches.Format.BASH_SHELL);
  }
}
