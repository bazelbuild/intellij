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
package com.google.idea.sdkcompat.clion;

import com.jetbrains.cidr.cpp.toolchains.CPPToolSet;
import java.io.File;

/** Adapter to bridge different SDK versions. #api182 */
public abstract class CPPToolSetAdapter extends CPPToolSet {

  public CPPToolSetAdapter(File workingDirectory) {
    super(Kind.MINGW, workingDirectory);
  }
}
