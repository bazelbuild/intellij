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
package com.google.idea.blaze.java.wizard;

import com.google.idea.blaze.base.ui.BlazeValidationResult;
import com.google.idea.blaze.base.wizard.BlazeProjectSettingsControl;
import com.google.idea.blaze.base.wizard.ImportResults;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.projectImport.ProjectImportWizardStep;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.io.File;

/**
 * Handles the following responsibilities:
 * <pre>
 * <ul>
 *   <li>allows end user to define external system config file to import from;</li>
 *   <li>processes the input and reacts accordingly - shows error message if the project is invalid or proceeds to the next screen;</li>
 * </ul>
 * </pre>
 *
 * @author Denis Zhdanov
 * @since 8/1/11 4:15 PM
 */
public class SelectExternalProjectStep extends ProjectImportWizardStep {

  @NotNull
  private final JPanel component = new JPanel(new BorderLayout());

  @NotNull
  private final BlazeProjectSettingsControl control;

  private boolean settingsInitialised;

  public SelectExternalProjectStep(@NotNull WizardContext context) {
    super(context);
    control = new BlazeProjectSettingsControl(context.getDisposable());
  }

  @Override
  public JComponent getComponent() {
    return component;
  }

  @Override
  public void updateStep() {
    if (!settingsInitialised) {
      init();
    }
  }

  private void init() {
    BlazeNewJavaProjectImportBuilder builder = getBuilder();
    File fileToImport = new File(builder.getFileToImport());
    JPanel importControl = control.createComponent(fileToImport);
    this.component.add(importControl);
    settingsInitialised = true;
  }

  @Override
  public boolean validate() throws ConfigurationException {
    BlazeValidationResult validationResult = control.validate();
    if (validationResult.error != null) {
      throw new ConfigurationException(validationResult.error.getError());
    }
    return validationResult.success;
  }

  @Override
  public void updateDataModel() {
    ImportResults importResults = control.getResults();

    BlazeNewJavaProjectImportBuilder builder = getBuilder();
    WizardContext wizardContext = getWizardContext();

    builder.setImportSettings(importResults.importSettings);
    builder.setProjectView(importResults.projectView);
    wizardContext.setProjectName(importResults.projectName);
    wizardContext.setProjectFileDirectory(importResults.projectDataDirectory);
  }

  @Override
  @NotNull
  protected BlazeNewJavaProjectImportBuilder getBuilder() {
    BlazeNewJavaProjectImportBuilder builder =
      (BlazeNewJavaProjectImportBuilder)getWizardContext().getProjectBuilder();
    assert builder != null;
    return builder;
  }
}
