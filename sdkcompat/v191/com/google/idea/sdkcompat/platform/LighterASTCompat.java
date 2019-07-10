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
package com.google.idea.sdkcompat.platform;

import com.intellij.lang.LighterAST;
import com.intellij.util.indexing.FileContentImpl;

/**
 * Compat for {@link FileContentImpl#getLighterASTForPsiDependentIndex()}. Remove when #api191 is no
 * longer supported.
 */
public class LighterASTCompat {
  private LighterASTCompat() {}

  public static LighterAST getLighterAst(FileContentImpl fileContent) {
    return fileContent.getLighterASTForPsiDependentIndex();
  }
}
