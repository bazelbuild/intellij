/*
 * Copyright 2024 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.idea.sdkcompat.logging;

import java.util.logging.Logger;

/** #api213: Use {@code Logger.getGlobal()} directly into calling classes */
public class LoggerWrapper {

  private LoggerWrapper() {}

  public static void addHandler(HandlerWrapper handler) {
    Logger.getGlobal().addHandler(handler);
  }

  public static void removeHandler(HandlerWrapper handler) {
    Logger.getGlobal().removeHandler(handler);
  }
}
