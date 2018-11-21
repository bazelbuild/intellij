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
package com.google.idea.sdkcompat.openapi;

import com.intellij.util.messages.Topic;
import javax.annotation.Nullable;

/** #api181: ToolWindowManagerListener API changed in 2018.2 */
public interface ToolWindowManagerListenerAdapter {
  /** Dummy method for 'compatibility' with 2018.1 and earlier. Doesn't actually do anything. */
  default void toolWindowRegistered(String id) {}

  @Nullable
  static Topic<ToolWindowManagerListenerAdapter> getTopic() {
    return null;
  }
}
