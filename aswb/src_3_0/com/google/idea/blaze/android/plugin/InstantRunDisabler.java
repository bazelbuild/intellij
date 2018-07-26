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
package com.google.idea.blaze.android.plugin;

import com.android.tools.idea.fd.InstantRunConfiguration;
import com.intellij.openapi.components.ApplicationComponent;

/**
 * Temporary measure to disable instant run at startup.
 *
 * <p>Currently, the checkbox itself is disabled (but not unchecked). The permanent fix is:
 * http://ag/3367441, but that won't be available until 3.1. This fixes the issue until then.
 */
public class InstantRunDisabler implements ApplicationComponent {
  @Override
  public void initComponent() {
    InstantRunConfiguration.getInstance().INSTANT_RUN = false;
  }

  @Override
  public void disposeComponent() {}

  @Override
  public String getComponentName() {
    return "ASwB Instant Run Disabler";
  }
}
