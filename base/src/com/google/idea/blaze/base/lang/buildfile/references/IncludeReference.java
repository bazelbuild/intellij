/*
 * Copyright 2026 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.lang.buildfile.references;

import com.google.idea.blaze.base.lang.buildfile.psi.BuildFile;
import com.google.idea.blaze.base.lang.buildfile.psi.IncludeStatement;
import com.google.idea.blaze.base.lang.buildfile.psi.StringLiteral;
import com.intellij.openapi.vfs.VirtualFileFilter;
import com.intellij.psi.PsiElement;
import org.jspecify.annotations.NonNull;

/** Resolves include statements in MODULE.bazel files to *.MODULE.bazel fragment files. */
public class IncludeReference extends LabelReference {

  public IncludeReference(StringLiteral element, boolean soft) {
    super(element, soft);
  }

  @Override
  public Object @NonNull [] getVariants() {
    if (!validLabelLocation(myElement)) {
      return EMPTY_ARRAY;
    }

    String labelString = LabelUtils.trimToDummyIdentifier(myElement.getStringContents());
    return getFileLookups(labelString);
  }

  @Override
  protected boolean skylarkExtensionReference(StringLiteral element) {
    PsiElement parent = element.getParent();
    if (!(parent instanceof IncludeStatement includeStatement)) {
      return false;
    }
    return includeStatement.getImportPsiElement() == element;
  }

  @Override
  protected @NonNull VirtualFileFilter getAllowedFilesFilter(BuildFile parentFile, boolean hasColon) {
    return file ->
            (file.getName().endsWith(".MODULE.bazel")
                    && !file.getName().equals("MODULE.bazel")
                    && !file.getPath().equals(parentFile.getFilePath()))
                    || (hasColon && file.isDirectory());
  }
}
