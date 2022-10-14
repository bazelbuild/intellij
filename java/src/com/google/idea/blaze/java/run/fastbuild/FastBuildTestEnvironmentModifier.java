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
package com.google.idea.blaze.java.run.fastbuild;

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.command.info.BlazeInfo;
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.settings.BuildSystemName;
import com.google.idea.blaze.base.util.BuildSystemExtensionPoint;
import com.google.idea.blaze.java.fastbuild.FastBuildInfo;
import com.intellij.execution.ExecutionException;
import com.intellij.openapi.extensions.ExtensionPointName;

/** An extension point to modify the Fast Build test environment. */
public interface FastBuildTestEnvironmentModifier extends BuildSystemExtensionPoint {

  ExtensionPointName<FastBuildTestEnvironmentModifier> EP_NAME =
      ExtensionPointName.create("com.google.idea.blaze.FastBuildTestEnvironmentModifier");

  static ImmutableList<FastBuildTestEnvironmentModifier> getModifiers(
      BuildSystemName buildSystemName) {
    return BuildSystemExtensionPoint.getInstances(EP_NAME, buildSystemName);
  }

  void modify(
      ModifiableJavaCommand commandBuilder,
      Kind kind,
      FastBuildInfo fastBuildInfo,
      BlazeInfo blazeInfo)
      throws ExecutionException;
}
