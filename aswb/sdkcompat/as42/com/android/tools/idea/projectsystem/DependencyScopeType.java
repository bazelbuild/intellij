/*
 * Copyright 2021 The Bazel Authors. All rights reserved.
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
package com.android.tools.idea.projectsystem;

/**
 * Backport of DependencyScopeType that is available AS 2020.3+. ASwB never consumes
 * DependencyScopeType, so this class serves to eliminate compilation errors in older versions.
 */
public enum DependencyScopeType {
  MAIN,
  UNIT_TEST,
  ANDROID_TEST;
}
