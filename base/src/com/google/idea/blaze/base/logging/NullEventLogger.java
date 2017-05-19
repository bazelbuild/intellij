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
package com.google.idea.blaze.base.logging;

import java.util.Map;
import org.jetbrains.annotations.Nullable;

/** No-op logger used when no logger is not available to receive logs. */
public class NullEventLogger implements EventLogger {
  static final NullEventLogger SINGLETON = new NullEventLogger();

  private NullEventLogger() {}

  @Override
  public boolean isApplicable() {
    return true;
  }

  @Override
  public void log(
      Class<?> loggingClass,
      String eventType,
      Map<String, String> keyValues,
      @Nullable Long durationInNanos) {}
}
