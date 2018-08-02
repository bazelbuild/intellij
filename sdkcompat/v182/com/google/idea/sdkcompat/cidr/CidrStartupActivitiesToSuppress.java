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
 * #api173 -- After 2018.1, can remove along with CidrSymbolBuilderSuppressor. Configurations are
 * serialized/deserialized by OCWorkspace, so configs should be ready by the time
 * OCInitialTablesBuildingActivity wants to run.
 */
public class CidrStartupActivitiesToSuppress {
  public static final ImmutableList<Class<? extends StartupActivity>>
      STARTUP_ACTIVITIES_TO_SUPPRESS = ImmutableList.of();
}
