/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.res;

import com.android.resources.ResourceFolderType;

/** Fake {@link IdeResourceNameValidator} for 2.3, renamed from {@link ResourceNameValidator}. */
public class IdeResourceNameValidator {
  public final ResourceNameValidator delegate;

  public IdeResourceNameValidator(ResourceNameValidator delegate) {
    this.delegate = delegate;
  }

  public static IdeResourceNameValidator forFilename(
      ResourceFolderType type, String implicitExtension) {
    return new IdeResourceNameValidator(ResourceNameValidator.create(false, type));
  }

  public String getErrorText(String inputString) {
    return delegate.getErrorText(inputString);
  }
}
