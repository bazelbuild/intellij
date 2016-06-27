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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.google.idea.blaze.base.model.primitives.ExecutionRootPath;

import java.util.List;

/**
 * unfilteredCompilerOptions is a grab bag of options passed to the compiler. Do minimal parsing to extract what we need.
 */
final class UnfilteredCompilerOptions {
  private enum NextOption {ISYSTEM, FLAG}

  private final List<ExecutionRootPath> toolchainSysIncludes;
  private final List<String> toolchainFlags;

  public UnfilteredCompilerOptions(Iterable<String> unfilteredOptions) {
    List<String> toolchainSystemIncludePaths = Lists.newArrayList();
    toolchainFlags = Lists.newArrayList();
    splitUnfilteredCompilerOptions(unfilteredOptions, toolchainSystemIncludePaths, toolchainFlags);

    toolchainSysIncludes = Lists.newArrayList();
    for (String systemInclude : toolchainSystemIncludePaths) {
      toolchainSysIncludes.add(new ExecutionRootPath(systemInclude));
    }
  }

  public List<String> getToolchainFlags() {
    return toolchainFlags;
  }

  public List<ExecutionRootPath> getToolchainSysIncludes() {
    return toolchainSysIncludes;
  }

  @VisibleForTesting
  static void splitUnfilteredCompilerOptions(
    Iterable<String> unfilteredOptions,
    List<String> toolchainSysIncludes,
    List<String> toolchainFlags
  ) {
    NextOption nextOption = NextOption.FLAG;
    for (String unfilteredOption : unfilteredOptions) {
      // We are looking for either the flag pair "-isystem /path/to/dir" or the flag "-isystem/path/to/dir"
      //
      // blaze emits isystem flags in both formats. The latter isn't ideal but apparently it is accepted by GCC and will be emitted by
      // blaze under certain circumstances.
      if (nextOption == NextOption.ISYSTEM) {
        toolchainSysIncludes.add(unfilteredOption);
        nextOption = NextOption.FLAG;
      }
      else {
        if (unfilteredOption.equals("-isystem")) {
          nextOption = NextOption.ISYSTEM;
        }
        else if (unfilteredOption.startsWith("-isystem")) {
          String iSystemIncludePath = unfilteredOption.substring("-isystem".length());
          toolchainSysIncludes.add(iSystemIncludePath);
        }
        else {
          toolchainFlags.add(unfilteredOption);
          nextOption = NextOption.FLAG;
        }
      }
    }
  }
}
