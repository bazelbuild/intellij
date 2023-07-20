/*
 * Copyright 2023 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.qsync.settings;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.util.NlsContexts.ConfigurableName;
import com.intellij.ui.components.JBCheckBox;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JPanel;
import org.jetbrains.annotations.Nullable;

/** A configuration page for the settings dialog for query sync. */
public class QuerySyncConfigurable implements Configurable {

  private final JPanel panel;
  private final JBCheckBox displayDetailsText;

  private final QuerySyncSettings settings = QuerySyncSettings.getInstance();

  public QuerySyncConfigurable() {
    panel = new JPanel();
    panel.setLayout(new BoxLayout(panel, BoxLayout.PAGE_AXIS));

    displayDetailsText = new JBCheckBox("Display detailed dependency text in the editor");
    panel.add(displayDetailsText);
  }

  @Override
  public @ConfigurableName String getDisplayName() {
    return "Query Sync";
  }

  @Override
  public @Nullable JComponent createComponent() {
    return panel;
  }

  @Override
  public boolean isModified() {
    return settings.showDetailedInformationInEditor != displayDetailsText.isSelected();
  }

  @Override
  public void apply() throws ConfigurationException {
    settings.showDetailedInformationInEditor = displayDetailsText.isSelected();
  }

  @Override
  public void reset() {
    displayDetailsText.setSelected(settings.showDetailedInformationInEditor);
  }
}
