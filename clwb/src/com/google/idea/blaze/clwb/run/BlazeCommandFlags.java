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
package com.google.idea.blaze.clwb.run;

import com.google.common.base.Objects;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.ui.UiUtil;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.util.execution.ParametersListUtil;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JTextArea;
import org.jdom.Element;

final class BlazeCommandFlags implements JDOMExternalizable {
  public static final class Editor {
    private final JTextArea blazeFlagsField = new JTextArea(5, 0);
    private final JTextArea exeFlagsField = new JTextArea(5, 0);

    public JComponent getEditorComponent() {
      return UiUtil.createBox(
          new JLabel("Blaze flags:"),
          blazeFlagsField,
          new JLabel("Executable flags:"),
          exeFlagsField);
    }

    public void setText(BlazeCommandFlags blazeCommandFlags) {
      blazeFlagsField.setText(ParametersListUtil.join(blazeCommandFlags.getBlazeFlags()));
      exeFlagsField.setText(ParametersListUtil.join(blazeCommandFlags.getExeFlags()));
    }

    public BlazeCommandFlags getBlazeCommandFlags() {
      ImmutableList<String> blazeFlags =
          ImmutableList.copyOf(
              ParametersListUtil.parse(Strings.nullToEmpty(blazeFlagsField.getText())));
      ImmutableList<String> exeFlags =
          ImmutableList.copyOf(
              ParametersListUtil.parse(Strings.nullToEmpty(exeFlagsField.getText())));
      return new BlazeCommandFlags(blazeFlags, exeFlags);
    }
  }

  private static final String USER_BLAZE_FLAG_TAG = "blaze-user-flag";
  private static final String USER_EXE_FLAG_TAG = "blaze-user-exe-flag";

  private ImmutableList<String> blazeFlags = ImmutableList.of();
  private ImmutableList<String> exeFlags = ImmutableList.of();

  public BlazeCommandFlags() {
    this.blazeFlags = ImmutableList.of();
    this.exeFlags = ImmutableList.of();
  }

  public BlazeCommandFlags(ImmutableList<String> blazeFlags, ImmutableList<String> exeFlags) {
    this.blazeFlags = blazeFlags;
    this.exeFlags = exeFlags;
  }

  public ImmutableList<String> getBlazeFlags() {
    return blazeFlags;
  }

  public ImmutableList<String> getExeFlags() {
    return exeFlags;
  }

  @Override
  public void readExternal(Element element) throws InvalidDataException {
    blazeFlags = loadUserFlags(element, USER_BLAZE_FLAG_TAG);
    exeFlags = loadUserFlags(element, USER_EXE_FLAG_TAG);
  }

  private static ImmutableList<String> loadUserFlags(Element root, String tag) {
    ImmutableList.Builder<String> flagsBuilder = ImmutableList.builder();
    for (Element e : root.getChildren(tag)) {
      String flag = e.getTextTrim();
      if (flag != null && !flag.isEmpty()) {
        flagsBuilder.add(flag);
      }
    }
    return flagsBuilder.build();
  }

  @Override
  public void writeExternal(Element element) throws WriteExternalException {
    saveUserFlags(element, blazeFlags, USER_BLAZE_FLAG_TAG);
    saveUserFlags(element, exeFlags, USER_EXE_FLAG_TAG);
  }

  private static void saveUserFlags(Element root, List<String> flags, String tag) {
    for (String flag : flags) {
      Element child = new Element(tag);
      child.setText(flag);
      root.addContent(child);
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    BlazeCommandFlags that = (BlazeCommandFlags) o;
    return Objects.equal(blazeFlags, that.blazeFlags) && Objects.equal(exeFlags, that.exeFlags);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(blazeFlags, exeFlags);
  }
}
