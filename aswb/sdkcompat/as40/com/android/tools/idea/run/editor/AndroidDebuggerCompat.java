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
package com.android.tools.idea.run.editor;

import com.android.sdklib.AndroidVersion;
import com.android.tools.idea.run.tasks.DebugConnectorTask;
import com.intellij.execution.runners.ExecutionEnvironment;
import java.util.Set;
import org.jetbrains.android.facet.AndroidFacet;

/** Compat class for {@link AndroidDebugger} */
public final class AndroidDebuggerCompat {
  private AndroidDebuggerCompat() {}

  @SuppressWarnings({"unchecked", "rawtypes"}) // Raw type from upstream.
  public static DebugConnectorTask getConnectDebuggerTask(
      AndroidDebugger androidDebugger,
      ExecutionEnvironment env,
      AndroidVersion o,
      Set<String> packageIds,
      AndroidFacet facet,
      AndroidDebuggerState androidDebuggerState,
      String id) {
    return androidDebugger.getConnectDebuggerTask(
        env, o, packageIds, facet, androidDebuggerState, id);
  }
}
