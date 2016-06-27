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

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.async.process.ExternalTask;
import com.google.idea.blaze.base.io.FileAttributeProvider;
import com.google.idea.blaze.base.settings.BlazeUserSettings;
import com.google.idea.blaze.base.settings.ui.BlazeUserSettingsConfigurable;
import com.google.idea.blaze.base.ui.BlazeValidationResult;
import com.google.idea.blaze.base.ui.FileSelectorWithStoredHistory;
import com.google.idea.blaze.base.wizard2.BlazeNewProjectBuilder;
import com.intellij.ui.components.panels.VerticalLayout;

import javax.annotation.Nullable;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.io.ByteArrayOutputStream;
import java.io.File;

/**
 * UI for selecting the build system binary during the import process.
 */
public class SelectBazelBinaryControl {

  public final BlazeNewProjectBuilder builder;

  private boolean uiInitialized = false;
  private JPanel component;
  private FileSelectorWithStoredHistory bazelBinaryPath;

  public SelectBazelBinaryControl(BlazeNewProjectBuilder builder) {
    this.builder = builder;
  }

  public JComponent getUiComponent() {
    if (!uiInitialized) {
      initUi();
      uiInitialized = true;
    }
    return component;
  }

  private void initUi() {
    bazelBinaryPath = FileSelectorWithStoredHistory.create(
      BlazeUserSettingsConfigurable.BAZEL_BINARY_PATH_KEY,
      "Specify the bazel binary path");
    bazelBinaryPath.setText(getInitialBinaryPath());

    component = new JPanel(new VerticalLayout(4));
    component.add(new JLabel("Select a bazel binary"));
    component.add(new JSeparator());

    JPanel content = new JPanel(new VerticalLayout(12));
    content.setBorder(new EmptyBorder(50, 100, 0, 100));
    component.add(content);

    content.add(new JLabel("Specify a bazel binary to be used for all bazel projects"));
    content.add(bazelBinaryPath);
  }

  public BlazeValidationResult validate() {
    String binaryPath = getBazelPath();
    if (Strings.isNullOrEmpty(binaryPath)) {
      return BlazeValidationResult.failure("Select a bazel binary");
    }
    if (!FileAttributeProvider.getInstance().isFile(new File(binaryPath))) {
      return BlazeValidationResult.failure("Invalid bazel binary: file does not exist");
    }
    return BlazeValidationResult.success();
  }

  public void commit() {
    if (!Strings.isNullOrEmpty(getBazelPath())) {
      BlazeUserSettings.getInstance().setBazelBinaryPath(getBazelPath());
    }
  }

  private String getBazelPath() {
    String text = bazelBinaryPath.getText();
    return text != null ? text.trim() : "";
  }

  private static String getInitialBinaryPath() {
    String existingPath = BlazeUserSettings.getInstance().getBazelBinaryPath();
    if (existingPath != null) {
      return existingPath;
    }
    return guessBinaryPath();
  }

  /**
   * Try to guess an initial binary path
   */
  @Nullable
  private static String guessBinaryPath() {
    ByteArrayOutputStream stdout = new ByteArrayOutputStream();
    int retVal = ExternalTask.builder(new File("/"), ImmutableList.of("which", "bazel"))
      .stdout(stdout)
      .build()
      .run();

    if (retVal != 0) {
      return null;
    }
    return stdout.toString().trim();
  }

}
