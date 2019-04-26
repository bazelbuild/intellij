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
package com.google.idea.blaze.base.run;

/**
 * Looks for a test rule whose name matches the relative path from the directory constaining the
 * BUILD file to the source file, possibly using underscores instead of slashes.
 *
 * <p>For example, if the source file is //path/to/BUILD/path/to/Source.java, then a rule named
 * //path/to/BUILD:path_to_Source would pass this filter.
 */
public class TargetNameWithUnderscoresHeuristic extends TargetNameHeuristic {
  @Override
  protected boolean matches(String filePathWithoutExtension, String targetName) {
    return super.matches(filePathWithoutExtension, targetName.replace('_', '/'));
  }
}
