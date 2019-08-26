/*
 * Copyright 2019 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.android.run.runner;

import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.run.TargetSelectionMode;
import com.android.tools.idea.run.editor.DeployTargetProvider;

/** An indirection to provide a class compatible with #api3.5 and prior. */
public class BlazeAndroidRunConfigurationDeployTargetManager
    extends BlazeAndroidRunConfigurationDeployTargetManagerBase {
  public BlazeAndroidRunConfigurationDeployTargetManager(boolean isAndroidTest) {
    super(isAndroidTest);
  }

  @Override
  protected DeployTargetProvider getCurrentDeployTargetProvider() {
    TargetSelectionMode targetSelectionMode;
    if (StudioFlags.SELECT_DEVICE_SNAPSHOT_COMBO_BOX_VISIBLE.get()) {
      targetSelectionMode = TargetSelectionMode.DEVICE_AND_SNAPSHOT_COMBO_BOX;
    } else {
      targetSelectionMode = TargetSelectionMode.SHOW_DIALOG;
    }
    DeployTargetProvider targetProvider = getDeployTargetProvider(targetSelectionMode.name());
    assert targetProvider != null;
    return targetProvider;
  }
}
