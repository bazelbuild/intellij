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
package com.google.idea.blaze.java.fastbuild;

import java.io.File;
import java.util.Collection;
import java.util.List;
import javax.tools.DiagnosticListener;
import javax.tools.JavaFileObject;

/**
 * A small wrapper around javac.
 *
 * <p>This interface is used by two classloaders (the plugin's and the compiler's) whose only shared
 * classes are those from the JRE, so it mustn't import anything besides that.
 */
// TODO(plumpy): have FastBuildJavacImpl implement JavaCompiler instead, which would get rid of the
// need for this class. It's a lot more complicated to do that, however.
interface FastBuildJavac {

  boolean compile(
      List<String> args,
      Collection<File> sources,
      DiagnosticListener<? super JavaFileObject> listener);
}
