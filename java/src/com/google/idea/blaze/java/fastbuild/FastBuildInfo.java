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
package com.google.idea.blaze.java.fastbuild;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.idea.blaze.base.command.info.BlazeInfo;
import com.google.idea.blaze.base.model.primitives.Label;
import java.io.File;
import java.util.List;
import java.util.Map;

/** Information about a completed build. */
@AutoValue
public abstract class FastBuildInfo {

  public abstract Label label();

  public abstract File deployJar();

  public abstract ImmutableList<File> classpath();

  public abstract ImmutableMap<Label, FastBuildBlazeData> blazeData();

  public abstract BlazeInfo blazeInfo();

  public static FastBuildInfo create(
      Label label,
      File deployJar,
      List<File> classpath,
      Map<Label, FastBuildBlazeData> blazeData,
      BlazeInfo blazeInfo) {
    return new AutoValue_FastBuildInfo(
        label,
        deployJar,
        ImmutableList.copyOf(classpath),
        ImmutableMap.copyOf(blazeData),
        blazeInfo);
  }
}
