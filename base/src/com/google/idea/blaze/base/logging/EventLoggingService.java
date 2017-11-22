/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.base.logging;

import com.google.idea.blaze.base.logging.utils.SyncStats;
import com.intellij.openapi.components.ServiceManager;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;

/**
 * Forwards event logs to any {@link EventLogger}s available. This indirection exists so that {@link
 * EventLogger} can have a minimal API surface.
 */
public interface EventLoggingService {

  static Optional<EventLoggingService> getInstance() {
    return Optional.ofNullable(ServiceManager.getService(EventLoggingService.class));
  }

  void log(SyncStats syncStats);

  void logEvent(Class<?> loggingClass, String eventType, Map<String, String> keyValues);

  void logEvent(
      Class<?> loggingClass,
      String eventType,
      Map<String, String> keyValues,
      @Nullable Long durationInNanos);
}
