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
package com.google.idea.blaze.java.settings;

import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.ui.BlazeUserSettingsContributor;
import com.intellij.openapi.ui.Messages;
import com.intellij.uiDesigner.core.GridConstraints;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JPanel;

/** Contributes java-specific settings. */
public class BlazeJavaUserSettingsContributor implements BlazeUserSettingsContributor {
  private JCheckBox useJarCache;
  private JCheckBox attachSourcesByDefault;
  private JCheckBox attachSourcesOnDemand;
  private final ImmutableList<JComponent> components;

  BlazeJavaUserSettingsContributor() {
    useJarCache = new JCheckBox();
    useJarCache.setText(
        String.format(
            "Use a local jar cache. More robust, but we can miss %s changes made outside the IDE.",
            Blaze.defaultBuildSystemName()));

    attachSourcesByDefault = new JCheckBox();
    attachSourcesByDefault.setSelected(false);
    attachSourcesByDefault.setText(
        "Automatically attach sources on project sync (WARNING: increases index time by 100%+)");

    attachSourcesByDefault.addActionListener(
        (event) -> {
          BlazeJavaUserSettings settings = BlazeJavaUserSettings.getInstance();
          if (attachSourcesByDefault.isSelected() && !settings.getAttachSourcesByDefault()) {
            int result =
                Messages.showOkCancelDialog(
                    "You are turning on source jars by default. "
                        + "This setting increases indexing time by "
                        + ">100%, can cost ~1GB RAM, and will increase "
                        + "project reopen time significantly. "
                        + "Are you sure you want to proceed?",
                    "Turn On Sources By Default?", null);
            if (result != Messages.OK) {
              attachSourcesByDefault.setSelected(false);
            }
          }
        });

    attachSourcesOnDemand = new JCheckBox();
    attachSourcesOnDemand.setSelected(false);
    attachSourcesOnDemand.setText("Automatically attach sources when you open decompiled source");

    components = ImmutableList.of(useJarCache, attachSourcesOnDemand, attachSourcesByDefault);
  }

  @Override
  public void apply() {
    BlazeJavaUserSettings settings = BlazeJavaUserSettings.getInstance();
    settings.setUseJarCache(useJarCache.isSelected());
    settings.setAttachSourcesByDefault(attachSourcesByDefault.isSelected());
    settings.setAttachSourcesOnDemand(attachSourcesOnDemand.isSelected());
  }

  @Override
  public void reset() {
    BlazeJavaUserSettings settings = BlazeJavaUserSettings.getInstance();
    useJarCache.setSelected(settings.getUseJarCache());
    attachSourcesByDefault.setSelected(settings.getAttachSourcesByDefault());
    attachSourcesOnDemand.setSelected(settings.getAttachSourcesOnDemand());
  }

  @Override
  public boolean isModified() {
    BlazeJavaUserSettings settings = BlazeJavaUserSettings.getInstance();
    return !Objects.equal(useJarCache.isSelected(), settings.getUseJarCache())
        || !Objects.equal(attachSourcesByDefault.isSelected(), settings.getAttachSourcesByDefault())
        || !Objects.equal(attachSourcesOnDemand.isSelected(), settings.getAttachSourcesOnDemand());
  }

  @Override
  public int getRowCount() {
    return components.size();
  }

  @Override
  public int addComponents(JPanel panel, int rowi) {
    for (JComponent contributedComponent : components) {
      panel.add(
          contributedComponent,
          new GridConstraints(
              rowi++,
              0,
              1,
              2,
              GridConstraints.ANCHOR_NORTHWEST,
              GridConstraints.FILL_NONE,
              GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
              GridConstraints.SIZEPOLICY_FIXED,
              null,
              null,
              null,
              0,
              false));
    }
    return rowi;
  }

  static class BlazeJavaUserSettingsProvider implements BlazeUserSettingsContributor.Provider {
    @Override
    public BlazeUserSettingsContributor getContributor() {
      return new BlazeJavaUserSettingsContributor();
    }
  }
}
