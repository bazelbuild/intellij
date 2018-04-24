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

import com.google.common.collect.ImmutableList;
import com.intellij.openapi.startup.StartupActivity;

/**
 * cidr-lang usually registers some StartupActivity instances that get run on project open to
 * rebuild symbols. This causes problems with blaze projects because this happens before the project
 * configuration has been set up. CLwB/ASwB trigger a symbol rebuild after the startup sync.
 */
public class CidrStartupActivitiesToSuppress {
  public static final ImmutableList<Class<? extends StartupActivity>>
      STARTUP_ACTIVITIES_TO_SUPPRESS =
          ImmutableList.of(
              com.jetbrains.cidr.lang.symbols.symtable.OCInitialTablesBuildingActivity.class);
}
