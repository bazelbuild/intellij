/*
 * Copyright 2024 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.projectview.section.sections;

import com.google.idea.blaze.base.projectview.section.ScalarSection;
import com.google.idea.blaze.base.projectview.section.SectionKey;
import com.google.idea.blaze.base.projectview.section.SectionParser;

public class EnablePythonCodegenSupport {
  public static final SectionKey<Boolean, ScalarSection<Boolean>> KEY =
      SectionKey.of("enable_python_codegen_support");
  public static final SectionParser PARSER = new BooleanSectionParser(
          KEY,
                  """
                  If set to true, enables Python codegen support.
                  More info <a href=https://github.com/bazelbuild/intellij/blob/master/docs/python/code-generators.md>here</a>
                  """
  );
}
