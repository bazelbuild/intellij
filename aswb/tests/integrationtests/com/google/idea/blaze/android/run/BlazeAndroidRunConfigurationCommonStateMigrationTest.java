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

package com.google.idea.blaze.android.run;

import static com.google.common.truth.Truth.assertThat;

import com.google.idea.blaze.android.BlazeAndroidIntegrationTestCase;
import java.io.StringReader;
import java.util.List;
import org.jdom.Element;
import org.jdom.input.SAXBuilder;
import org.jdom.output.Format;
import org.jdom.output.XMLOutputter;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for migrating the storage of deploy target state and debugger state in {@link
 * BlazeAndroidRunConfigurationCommonState} TODO Introduced in 1.12, remove in 1.14 when the
 * migration code in BlazeAndroidRunConfigurationCommonState is removed.
 */
@RunWith(JUnit4.class)
public class BlazeAndroidRunConfigurationCommonStateMigrationTest
    extends BlazeAndroidIntegrationTestCase {

  private static final String DEPLOY_TARGET_STATES_RAW_XML =
      "<android-deploy-target-states>"
          + "  <option name=\"USE_LAST_SELECTED_DEVICE\" value=\"true\" />"
          + "  <option name=\"PREFERRED_AVD\" value=\"some avd\" />"
          + "</android-deploy-target-states>";
  private static final String DEBUGGER_STATE_AUTO_RAW_XML =
      "<Auto>"
          + "  <option name=\"USE_JAVA_AWARE_DEBUGGER\" value=\"true\" />"
          + "  <option name=\"WORKING_DIR\" value=\"/some/directory\" />"
          + "  <option name=\"TARGET_LOGGING_CHANNELS\" value=\"some channels\" />"
          + "</Auto>";
  private static final String DEBUGGER_STATE_NATIVE_RAW_XML =
      "<Native>"
          + "  <option name=\"USE_JAVA_AWARE_DEBUGGER\" value=\"false\" />"
          + "  <option name=\"WORKING_DIR\" value=\"\" />"
          + "  <option name=\"TARGET_LOGGING_CHANNELS\""
          + "          value=\"lldb process:gdb-remote packets\" />"
          + "</Native>";
  private static final String DEBUGGER_STATE_JAVA_RAW_XML = "<Java />";
  private static final String DEBUGGER_STATE_HYBRID_RAW_XML =
      "<Hybrid>"
          + "  <option name=\"USE_JAVA_AWARE_DEBUGGER\" value=\"true\" />"
          + "  <option name=\"WORKING_DIR\" value=\"\" />"
          + "  <option name=\"TARGET_LOGGING_CHANNELS\""
          + "          value=\"lldb process:gdb-remote packets\" />"
          + "</Hybrid>";
  private static final String DEBUGGER_STATE_BLAZE_AUTO_RAW_XML =
      "<BlazeAuto>"
          + "  <option name=\"USE_JAVA_AWARE_DEBUGGER\" value=\"false\" />"
          + "  <option name=\"WORKING_DIR\" value=\"/some/other/directory\" />"
          + "  <option name=\"TARGET_LOGGING_CHANNELS\" value=\"some other channels\" />"
          + "</BlazeAuto>";

  private BlazeAndroidRunConfigurationCommonState state;
  private SAXBuilder saxBuilder;
  private XMLOutputter xmlOutputter;

  @Before
  public final void doSetup() throws Exception {
    state = new BlazeAndroidRunConfigurationCommonState(buildSystem().getName(), false);
    saxBuilder = new SAXBuilder();
    xmlOutputter = new XMLOutputter(Format.getCompactFormat());
  }

  private String formatRawXml(String rawXml) throws Exception {
    Element element =
        saxBuilder.build(new StringReader("<?xml version=\"1.0\"?>" + rawXml)).getRootElement();
    return xmlOutputter.outputString(element);
  }

  @Test
  public void readAndWriteShouldRemoveExtraElements() throws Exception {
    String oldXml =
        "<?xml version=\"1.0\"?>"
            + "<configuration blaze-native-debug=\"true\">"
            + "  <blaze-user-flag>--flag1</blaze-user-flag>"
            + "  <blaze-user-flag>--flag2</blaze-user-flag>"
            + "  <option name=\"USE_LAST_SELECTED_DEVICE\" value=\"true\" />"
            + "  <option name=\"PREFERRED_AVD\" value=\"some avd\" />"
            + DEBUGGER_STATE_AUTO_RAW_XML
            + DEBUGGER_STATE_NATIVE_RAW_XML
            + DEBUGGER_STATE_JAVA_RAW_XML
            + DEBUGGER_STATE_HYBRID_RAW_XML
            + DEBUGGER_STATE_BLAZE_AUTO_RAW_XML
            + "  <option name=\"USE_LAST_SELECTED_DEVICE\" value=\"true\" />"
            + "  <option name=\"PREFERRED_AVD\" value=\"some avd\" />"
            + DEBUGGER_STATE_AUTO_RAW_XML
            + DEBUGGER_STATE_NATIVE_RAW_XML
            + DEBUGGER_STATE_JAVA_RAW_XML
            + DEBUGGER_STATE_HYBRID_RAW_XML
            + DEBUGGER_STATE_BLAZE_AUTO_RAW_XML
            + "</configuration>";
    Element oldElement = saxBuilder.build(new StringReader(oldXml)).getRootElement();

    state.readExternal(oldElement);
    Element migratedElement = new Element("configuration");
    state.writeExternal(migratedElement);

    assertThat(migratedElement.getChildren()).hasSize(4);
    List<Element> flagElements = migratedElement.getChildren("blaze-user-flag");
    assertThat(flagElements).hasSize(2);

    Element deployTargetStatesElement = migratedElement.getChild("android-deploy-target-states");
    assertThat(xmlOutputter.outputString(deployTargetStatesElement))
        .isEqualTo(formatRawXml(DEPLOY_TARGET_STATES_RAW_XML));

    Element debuggerStatesElement = migratedElement.getChild("android-debugger-states");
    assertThat(debuggerStatesElement.getChildren()).hasSize(5);
  }
}
