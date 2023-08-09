/*
 * Copyright 2023 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.qsync.query;

import com.google.common.collect.ImmutableList;
import com.google.devtools.build.lib.query2.proto.proto2api.Build.Rule;

/**
 * Interface for providing information about deps implicit for certain rules (e.g. runtime jars for
 * proto java libraries).
 */
public interface ImplicitDepsProvider {
  ImmutableList<String> forRule(Rule rule);

  String name();

  /**
   * Version of the provider. If the provider implementation changes, the version should be changed
   * to prevent possible errors during partial syncs.
   */
  int version();

  default String getVersionWithName() {
    return String.format("%s:%d", name(), version());
  }

  ImplicitDepsProvider EMPTY =
      new ImplicitDepsProvider() {

        private static final int VERSION = 0;

        @Override
        public ImmutableList<String> forRule(Rule rule) {
          return ImmutableList.of();
        }

        @Override
        public String name() {
          return "Default";
        }

        @Override
        public int version() {
          return VERSION;
        }
      };
}
