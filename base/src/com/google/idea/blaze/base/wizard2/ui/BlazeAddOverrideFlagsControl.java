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
package com.google.idea.blaze.base.wizard2.ui;

import com.google.idea.blaze.base.ui.UiUtil;
import com.google.idea.blaze.base.wizard2.*;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.ui.TextComponentAccessor;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.ui.components.panels.VerticalLayout;
import com.intellij.util.ui.JBUI;

import javax.swing.*;
import java.awt.*;
import java.io.File;

/** UI for setting the override repository flags during the import process. */
public class BlazeAddOverrideFlagsControl {

    private static final FileChooserDescriptor PROJECT_FOLDER_DESCRIPTOR =
            new FileChooserDescriptor(false, true, false, false, false, false);
    private TextFieldWithBrowseButton flagsPathTextField;
    private JTextField flagsRepoNameTextField;
    private final JPanel canvas;

  public BlazeAddOverrideFlagsControl(BlazeNewProjectBuilder builder) {
      JPanel canvas = new JPanel(new VerticalLayout(4));

      flagsPathTextField = new TextFieldWithBrowseButton();
      flagsRepoNameTextField = new JTextField();
      JLabel selectOverridesLabel = new JLabel("Select Overrides");
      JLabel flagsPathLabel = new JLabel("Override repository flag path:");
      JLabel flagsRepoNameLabel = new JLabel("Override repository name:");
      flagsPathTextField.setName("override-flags-path-field");
      flagsRepoNameTextField.setName("override-flags-repo-name-field");
      flagsPathTextField.addBrowseFolderListener(
              "",
              builder.getBuildSystemName() + " override repository flags",
              null,
              PROJECT_FOLDER_DESCRIPTOR,
              TextComponentAccessor.TEXT_FIELD_WHOLE_TEXT,
              false);
      final String flagsPathToolTipText = "Set override flag path here";
      final String flagsTextFieldToolTipText = "Select repository to override.";
      final String flagsNameToolTipText = "Set override flag name here";
      flagsPathTextField.setToolTipText(flagsTextFieldToolTipText);
      flagsPathLabel.setToolTipText(flagsPathToolTipText);
      flagsRepoNameTextField.setToolTipText(flagsNameToolTipText);

      canvas.add(selectOverridesLabel);
      canvas.add(new JSeparator());

      JPanel flagsPathBox = new JPanel(new VerticalLayout(4));
      JPanel flagsNameBox = new JPanel(new VerticalLayout(4));
      flagsPathBox.add(flagsPathLabel);
      flagsPathBox.add(flagsPathTextField);
      flagsNameBox.add(flagsRepoNameLabel);
      flagsNameBox.add(flagsRepoNameTextField);

      JPanel inner = new JPanel(new GridLayout(1,2));
      inner.add(flagsPathBox);
      inner.add(flagsNameBox);
      canvas.add(inner);

      canvas.setBorder(JBUI.Borders.empty(20,20,0,20));

      this.canvas= canvas;
  }

  public JComponent getUiComponent() { return canvas; }

  public void validateAndUpdateModel(BlazeNewProjectBuilder builder) throws ConfigurationException {
      File file = new File(getOverrideFlagPath());
      if (!getOverrideFlagPath().isEmpty()) {
          if (getOverrideFlagRepoName().isEmpty()) {
              throw new ConfigurationException("Must specify target name to override repository");
          }
          if (!file.exists()) {
              throw new ConfigurationException("This path does not exist.");
          }
          if (!file.isDirectory()) {
              throw new ConfigurationException("Specified override repository path is a file, not a directory");
          }
          if (builder.getOverrideFlags().get(getOverrideFlagRepoName()) != null) {
              throw new ConfigurationException("You cannot create the same flag twice.");
          }
          if (builder.getOverrideFlags().containsValue(getOverrideFlagPath())) {
              throw new ConfigurationException("You cannot create the same flag twice.");
          }
          builder.addOverrideFlags(getOverrideFlagRepoName(), getOverrideFlagPath());
      }
  }
  public String getOverrideFlagRepoName() { return flagsRepoNameTextField.getText().trim();}
  public String getOverrideFlagPath() { return flagsPathTextField.getText().trim(); }

}