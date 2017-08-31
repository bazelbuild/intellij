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
package com.google.idea.sdkcompat.android.resources.actions;

import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.tools.idea.res.IdeResourceNameValidator;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFile;
import java.util.function.Function;
import javax.annotation.Nullable;
import org.jetbrains.android.actions.CreateXmlResourcePanel;
import org.jetbrains.android.actions.NewResourceCreationHandler;

/** Compatibility adapter for {@link NewResourceCreationHandler}. */
public abstract class NewResourceCreationHandlerAdapter implements NewResourceCreationHandler {
  public abstract CreateXmlResourcePanel createNewResourceValuePanelCompat(
      Module module,
      ResourceType resourceType,
      ResourceFolderType folderType,
      @Nullable String resourceName,
      @Nullable String resourceValue,
      boolean chooseName,
      boolean chooseValue,
      boolean chooseFilename,
      @Nullable VirtualFile defaultFile,
      @Nullable VirtualFile contextFile,
      Function<Module, IdeResourceNameValidator> nameValidatorFactory);

  @Override
  public CreateXmlResourcePanel createNewResourceValuePanel(
      Module module,
      ResourceType resourceType,
      ResourceFolderType folderType,
      @Nullable String resourceName,
      @Nullable String resourceValue,
      boolean chooseName,
      boolean chooseValue,
      boolean chooseFilename,
      @Nullable VirtualFile defaultFile,
      @Nullable VirtualFile contextFile,
      Function<Module, IdeResourceNameValidator> nameValidatorFactory) {
    return createNewResourceValuePanelCompat(
        module,
        resourceType,
        folderType,
        resourceName,
        resourceValue,
        chooseName,
        chooseValue,
        chooseFilename,
        defaultFile,
        contextFile,
        nameValidatorFactory);
  }
}
