/*
 * Copyright 2020 The Bazel Authors. All rights reserved.
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
package com.google.idea.sdkcompat.golang;

import com.goide.sdk.GoSdk;
import com.goide.sdk.GoSdkImpl;
import com.goide.sdk.GoSdkService;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.ultimate.UltimateVerifier;
import javax.annotation.Nullable;

/**
 * SDK compat for {@link GoSdkService}, used for integration test setup.
 *
 * <p>#api192: constructor changed in 2019.3
 */
public final class GoSdkServiceProvider {
  private GoSdkServiceProvider() {}

  public static GoSdkService newInstance(Project project) {
    return new GoSdkService(project, new UltimateVerifier()) {
      @Override
      public GoSdk getSdk(@Nullable Module module) {
        return new GoSdkImpl("/usr/lib/golang", null, null);
      }
    };
  }
}
