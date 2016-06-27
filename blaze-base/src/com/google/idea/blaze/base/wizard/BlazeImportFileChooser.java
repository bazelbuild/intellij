/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.wizard;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDialog;
import com.intellij.openapi.fileChooser.FileChooserFactory;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NonNls;

import javax.annotation.Nullable;

public final class BlazeImportFileChooser {
  private static final String WIZARD_TITLE = "Import Blaze Project";
  private static final String WIZARD_DESCRIPTION = "Select a workspace, a .blazeproject file, or a BUILD file to import";
  @NonNls
  private static final String LAST_IMPORTED_LOCATION = "last.imported.location";


  private static final class BlazeFileChooser extends FileChooserDescriptor {
    BlazeFileChooser() {
      super(true, true, false, false, false, false);
    }

    @Override
    public boolean isFileSelectable(VirtualFile file) {
      // Default implementation doesn't filter directories, we want to make sure only workspace roots are selectable
      return super.isFileSelectable(file) && ImportSource.canImport(file);
    }
  }

  private static FileChooserDescriptor createFileChooserDescriptor() {
    return new BlazeFileChooser()
      .withShowHiddenFiles(true) // Show root project view file
      .withHideIgnored(false)
      .withTitle(WIZARD_TITLE)
      .withDescription(WIZARD_DESCRIPTION)
      .withFileFilter(ImportSource::canImport);
  }

  @Nullable
  public static VirtualFile getFileToImport() {
    FileChooserDescriptor descriptor = createFileChooserDescriptor();
    FileChooserDialog chooser = FileChooserFactory.getInstance().createFileChooser(descriptor, null, null);
    VirtualFile toSelect = null;
    String lastLocation = PropertiesComponent.getInstance().getValue(LAST_IMPORTED_LOCATION);
    if (lastLocation != null) {
      toSelect = LocalFileSystem.getInstance().refreshAndFindFileByPath(lastLocation);
    }
    VirtualFile[] files = chooser.choose(null, toSelect);
    if (files.length == 0) {
      return null;
    }
    VirtualFile file = files[0];
    PropertiesComponent.getInstance().setValue(LAST_IMPORTED_LOCATION, file.getPath());
    return file;
  }
}
