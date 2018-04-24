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
package com.google.idea.common.experiments;

/**
 * Exposes a system property used to identify the current user as a blaze plugin developer.
 *
 * <p>Used for the purposes of dogfooding experimental features, or turning on additional logging.
 */
public final class InternalDevFlag {

  private static final String INTERNAL_DEV_SYSTEM_PROPERTY = "blaze.internal.plugin.dev";

  /** Set a system property marking the current user as an internal plugin dev. */
  public static void markUserAsInternalDev(boolean isInternalDev) {
    System.setProperty(INTERNAL_DEV_SYSTEM_PROPERTY, isInternalDev ? "true" : "false");
  }

  public static boolean isInternalDev() {
    return System.getProperty(INTERNAL_DEV_SYSTEM_PROPERTY, "false").equals("true");
  }
}
