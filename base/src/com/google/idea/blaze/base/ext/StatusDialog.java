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
package com.google.idea.blaze.base.ext;

import com.google.idea.blaze.ext.IntelliJExtService;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.AppUIUtil;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.scale.ScaleContext;
import com.intellij.util.ui.JBFont;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.util.Map;
import java.util.Map.Entry;
import javax.swing.Action;
import javax.swing.Box;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.SwingConstants;
import org.jetbrains.annotations.Nullable;

/** The status dialog for the connection to the intellij-ext service. */
public class StatusDialog extends DialogWrapper {

  private final Box box;

  private String content;

  public StatusDialog(@Nullable Project project, IntelliJExtService service) {
    super(project, false);
    this.box = Box.createVerticalBox();
    setResizable(false);
    setTitle("Extended IntelliJ Services");
    init();
    setContent("Loading...", null);
    new Thread(
            () -> {
              String message;
              Map<String, String> status = null;
              try {
                status = service.getStatus();
                message = service.getVersion();
              } catch (Exception e) {
                message = "Error\nCannot fetch status, see logs.";
              }
              String finalMessage = message;
              Map<String, String> finalStatus = status;
              UIUtil.invokeLaterIfNeeded(() -> setContent(finalMessage, finalStatus));
            })
        .start();
  }

  @Override
  protected @Nullable JComponent createCenterPanel() {
    Icon appIcon = AppUIUtil.loadApplicationIcon(ScaleContext.create(), 60);
    JLabel icon = new JLabel(appIcon);
    icon.setVerticalAlignment(SwingConstants.TOP);
    icon.setBorder(JBUI.Borders.empty(20, 12, 0, 24));
    box.setBorder(JBUI.Borders.empty(20, 0, 0, 20));

    return JBUI.Panels.simplePanel().addToLeft(icon).addToCenter(box);
  }

  @Override
  protected void createDefaultActions() {
    super.createDefaultActions();
    myOKAction =
        new OkAction() {
          @Override
          protected void doAction(ActionEvent e) {
            copyAboutInfoToClipboard();
            close(OK_EXIT_CODE);
          }
        };
    myOKAction.putValue(Action.NAME, "Copy and Close");
    myCancelAction.putValue(Action.NAME, "Close");
  }

  private void setContent(String message, Map<String, String> status) {
    String content = message;
    box.removeAll();
    String[] lines = message.split("\n");
    box.add(label(lines[0], JBFont.regular().asBold()));
    for (int i = 1; i < lines.length; i++) {
      box.add(label(lines[i], JBFont.small()));
    }
    box.add(Box.createVerticalStrut(10));
    if (status != null) {
      Box line = Box.createHorizontalBox();
      line.setAlignmentX(0.0f);
      for (Entry<String, String> e : status.entrySet()) {
        line.add(label(e.getKey(), JBFont.regular().asBold()));
        line.add(Box.createHorizontalStrut(5));
        line.add(label(e.getValue(), JBFont.small()));
        box.add(line);
        content += "\n" + e.getKey() + ": " + e.getValue();
      }
    }
    this.content = content;
    this.getContentPane().revalidate();
  }

  private static JLabel label(String text, JBFont font) {
    return new JBLabel(text).withFont(font);
  }

  private String getPlainText() {
    return content;
  }

  private void copyAboutInfoToClipboard() {
    try {
      CopyPasteManager.getInstance().setContents(new StringSelection(getPlainText()));
    } catch (Exception ignore) {
      // Ignore
    }
  }
}
