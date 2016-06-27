/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.android.resources.actions;

import com.android.SdkConstants;
import com.android.resources.ResourceFolderType;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.Sets;
import com.google.idea.blaze.android.sync.model.AndroidResourceModule;
import com.google.idea.blaze.android.sync.model.BlazeAndroidSyncData;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.rulemaps.SourceToRuleMap;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiManager;
import com.intellij.ui.ComboboxWithBrowseButton;
import com.intellij.ui.components.JBLabel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.util.Set;

/**
 * Utilities for setting up create resource actions and dialogs.
 */
class BlazeCreateResourceUtils {

  private static final String PLACEHOLDER_TEXT = "choose a res/ directory with dropdown or browse button";

  static void setupResDirectoryChoices(@NotNull Project project, @Nullable VirtualFile contextFile,
                                       @NotNull JBLabel resDirLabel,
                                       @NotNull ComboboxWithBrowseButton resDirComboAndBrowser) {
    resDirComboAndBrowser.addBrowseFolderListener(
      project, FileChooserDescriptorFactory.createSingleFolderDescriptor());
    BlazeProjectData blazeProjectData = BlazeProjectDataManager.getInstance(project).getBlazeProjectData();
    if (blazeProjectData != null) {
      BlazeAndroidSyncData syncData = blazeProjectData.syncState.get(BlazeAndroidSyncData.class);
      if (syncData != null) {
        ImmutableCollection<Label> labelsRelatedToContext = null;
        File fileFromContext = null;
        if (contextFile != null) {
          fileFromContext = VfsUtilCore.virtualToIoFile(contextFile);
          labelsRelatedToContext = SourceToRuleMap.getInstance(project).getTargetsForSourceFile(fileFromContext);
          if (labelsRelatedToContext.isEmpty()) {
            labelsRelatedToContext = null;
          }
        }
        // Sort:
        // - the "closest" thing to the contextFile at the top
        // - the rest of the direct dirs, then transitive dirs of the context rules, then any known res dir in the project
        //   as a backup, in alphabetical order.
        Set<File> resourceDirs = Sets.newTreeSet();
        Set<File> transitiveDirs = Sets.newTreeSet();
        Set<File> allResDirs = Sets.newTreeSet();
        for (AndroidResourceModule androidResourceModule : syncData.importResult.androidResourceModules) {
          // labelsRelatedToContext should include deps, but as a first pass we only check the rules themselves
          // for resources. If we come up empty, then have anyResDir as a backup.
          allResDirs.addAll(androidResourceModule.transitiveResources);
          if (labelsRelatedToContext != null && !labelsRelatedToContext.contains(androidResourceModule.label)) {
            continue;
          }
          for (File resDir : androidResourceModule.resources) {
            resourceDirs.add(resDir);
          }
          for (File resDir : androidResourceModule.transitiveResources) {
            transitiveDirs.add(resDir);
          }
        }
        // No need to show some directories twice.
        transitiveDirs.removeAll(resourceDirs);
        File closestDirToContext = null;
        if (fileFromContext != null) {
          closestDirToContext = findClosestDirToContext(fileFromContext.getPath(), resourceDirs);
          closestDirToContext = closestDirToContext != null ? closestDirToContext :
                                findClosestDirToContext(fileFromContext.getPath(), transitiveDirs);
        }
        JComboBox resDirCombo = resDirComboAndBrowser.getComboBox();
        if (!resourceDirs.isEmpty() || !transitiveDirs.isEmpty()) {
          for (File resourceDir : resourceDirs) {
            resDirCombo.addItem(resourceDir);
          }
          for (File resourceDir : transitiveDirs) {
            resDirCombo.addItem(resourceDir);
          }
        }
        else {
          for (File resourceDir : allResDirs) {
            resDirCombo.addItem(resourceDir);
          }
        }
        // Allow the user to browse and overwrite some of the entries.
        resDirCombo.setEditable(true);
        if (closestDirToContext != null) {
          resDirCombo.setSelectedItem(closestDirToContext);
        }
        else {
          String placeHolder = PLACEHOLDER_TEXT;
          resDirCombo.insertItemAt(placeHolder, 0);
          resDirCombo.setSelectedItem(placeHolder);
        }
        resDirComboAndBrowser.setVisible(true);
        resDirLabel.setVisible(true);
      }
    }
  }

  private static File findClosestDirToContext(String contextPath, Set<File> resourceDirs) {
    File closestDirToContext = null;
    int curStringDistance = Integer.MAX_VALUE;
    for (File resDir : resourceDirs) {
      int distance = StringUtil.difference(contextPath, resDir.getPath());
      if (distance < curStringDistance) {
        curStringDistance = distance;
        closestDirToContext = resDir;
      }
    }
    return closestDirToContext;
  }

  static PsiDirectory getResDirFromUI(Project project, ComboboxWithBrowseButton directoryCombo) {
    PsiManager psiManager = PsiManager.getInstance(project);
    Object selectedItem = directoryCombo.getComboBox().getSelectedItem();
    VirtualFile file = null;
    if(selectedItem instanceof File) {
      file = VfsUtil.findFileByIoFile((File)selectedItem, true);
    } else if (selectedItem instanceof String) {
      String selectedDir = (String)selectedItem;
      if (!selectedDir.equals(PLACEHOLDER_TEXT)) {
        file = VfsUtil.findFileByIoFile(new File(selectedDir), true);
      }
    }
    if (file != null) {
      return psiManager.findDirectory(file);
    }
    return null;
  }

   static VirtualFile getResDirFromDataContext(VirtualFile contextFile) {
    // Check if the contextFile is somewhere in the <path>/res/resType/foo.xml hierarchy and return <path>/res/.
    if (contextFile.isDirectory()) {
      if (contextFile.getName().equalsIgnoreCase(SdkConstants.FD_RES)) {
        return contextFile;
      }
      if (ResourceFolderType.getFolderType(contextFile.getName()) != null) {
        VirtualFile parent = contextFile.getParent();
        if (parent != null && parent.getName().equalsIgnoreCase(SdkConstants.FD_RES)) {
          return parent;
        }
      }
    }
    else {
      VirtualFile parent = contextFile.getParent();
      if (parent != null && ResourceFolderType.getFolderType(parent.getName()) != null) {
        // Otherwise, the contextFile is a file w/ a parent that is plausible. Recurse one level, on the parent.
        return getResDirFromDataContext(parent);
      }
    }
    // Otherwise, it may be too ambiguous to figure out (e.g., we're in a .java file).
    return null;
  }
}
