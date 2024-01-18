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

import com.google.idea.blaze.base.command.info.BlazeInfo;
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.java.AndroidBlazeRules.RuleTypes;
import com.google.idea.blaze.java.fastbuild.FastBuildInfo;
import com.intellij.execution.ExecutionException;

abstract class AndroidLocalTestEnvironmentModifier implements FastBuildTestEnvironmentModifier {

  @Override
  public void modify(
      ModifiableJavaCommand commandBuilder,
      Kind kind,
      FastBuildInfo fastBuildInfo,
      BlazeInfo blazeInfo)
      throws ExecutionException {
    if (!kind.equals(RuleTypes.ANDROID_LOCAL_TEST.getKind())
        && !kind.equals(RuleTypes.KT_ANDROID_LOCAL_TEST.getKind())) {
      return;
    }

    commandBuilder
        .addSystemProperty("robolectric.offline", "true")
        .addSystemProperty(
            "robolectric-deps.properties", getRobolectricDepsProperties(fastBuildInfo))
        .addSystemProperty("use_framework_manifest_parser", "true")
        .addSystemProperty(
            "org.robolectric.packagesToNotAcquire", "com.google.testing.junit.runner.util");
  }

  abstract String getRobolectricDepsProperties(FastBuildInfo fastBuildInfo)
      throws ExecutionException;
}
