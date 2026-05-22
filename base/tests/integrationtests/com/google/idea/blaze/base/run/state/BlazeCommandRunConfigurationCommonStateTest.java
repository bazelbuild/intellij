/*
 * Copyright 2026 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.run.state;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.idea.blaze.base.BlazeIntegrationTestCase;
import com.google.idea.blaze.base.command.BlazeCommandName;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.BlazeImportSettings;
import com.google.idea.blaze.base.settings.BlazeImportSettings.ProjectType;
import com.google.idea.blaze.base.settings.BlazeImportSettingsManager;
import com.google.idea.blaze.base.settings.BuildSystemName;
import org.jdom.Element;
import org.jdom.output.Format;
import org.jdom.output.XMLOutputter;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link BlazeCommandRunConfigurationCommonState}. */
@RunWith(JUnit4.class)
public class BlazeCommandRunConfigurationCommonStateTest extends BlazeIntegrationTestCase {
  private static final BlazeImportSettings DUMMY_IMPORT_SETTINGS =
      new BlazeImportSettings("", "", "", "", BuildSystemName.Blaze, ProjectType.ASPECT_SYNC);
  private static final BlazeCommandName COMMAND = BlazeCommandName.fromString("command");

  private BlazeCommandRunConfigurationCommonState state;

  @Before
  public void init() {
    registerProjectService(BlazeImportSettingsManager.class, new BlazeImportSettingsManager(getProject()));
    BlazeImportSettingsManager.getInstance(getProject()).setImportSettings(DUMMY_IMPORT_SETTINGS);

    state = new BlazeCommandRunConfigurationCommonState(Blaze.getBuildSystemName(getProject()));
  }

  @Test
  public void readAndWriteShouldMatch() throws Exception {
    state.getCommandState().setCommand(COMMAND);
    state.getTestFilterState().setTestFilter("Foo#bar");
    state.getExeFlagsState().setRawFlags(ImmutableList.of("--exeFlag1"));
    state.getUserEnvVarsState().setEnvVars(ImmutableMap.of("HELLO", "world"));

    Element element = new Element("test");
    state.writeExternal(element);
    BlazeCommandRunConfigurationCommonState readState =
        new BlazeCommandRunConfigurationCommonState(Blaze.getBuildSystemName(getProject()));
    readState.readExternal(element);

    assertThat(readState.getCommandState().getCommand()).isEqualTo(COMMAND);
    assertThat(readState.getTestFilterState().getTestFilter()).isEqualTo("Foo#bar");
    assertThat(readState.getExeFlagsState().getRawFlags()).containsExactly("--exeFlag1");
    assertThat(readState.getUserEnvVarsState().getData().getEnvs())
        .isEqualTo(ImmutableMap.of("HELLO", "world"));
  }

  @Test
  public void readAndWriteShouldHandleNulls() throws Exception {
    Element element = new Element("test");
    state.writeExternal(element);
    BlazeCommandRunConfigurationCommonState readState =
        new BlazeCommandRunConfigurationCommonState(Blaze.getBuildSystemName(getProject()));
    readState.readExternal(element);

    assertThat(readState.getCommandState().getCommand())
        .isEqualTo(state.getCommandState().getCommand());
    assertThat(readState.getTestFilterState().getTestFilter())
        .isEqualTo(state.getTestFilterState().getTestFilter());
    assertThat(readState.getExeFlagsState().getRawFlags())
        .isEqualTo(state.getExeFlagsState().getRawFlags());
    assertThat(readState.getUserEnvVarsState().getData().getEnvs())
        .isEqualTo(state.getUserEnvVarsState().getData().getEnvs());
  }

  @Test
  public void repeatedWriteShouldNotChangeElement() throws Exception {
    final XMLOutputter xmlOutputter = new XMLOutputter(Format.getCompactFormat());

    state.getCommandState().setCommand(COMMAND);
    state.getTestFilterState().setTestFilter("Foo#bar");
    state.getExeFlagsState().setRawFlags(ImmutableList.of("--exeFlag1"));

    Element firstWrite = new Element("test");
    state.writeExternal(firstWrite);
    Element secondWrite = firstWrite.clone();
    state.writeExternal(secondWrite);

    assertThat(xmlOutputter.outputString(secondWrite))
        .isEqualTo(xmlOutputter.outputString(firstWrite));
  }

  @Test
  public void editorApplyToAndResetFromShouldMatch() throws Exception {
    RunConfigurationStateEditor editor = state.getEditor(getProject());

    state.getCommandState().setCommand(COMMAND);
    state.getTestFilterState().setTestFilter("Foo#bar");
    state.getExeFlagsState().setRawFlags(ImmutableList.of("--exeFlag1", "--exeFlag2"));

    editor.resetEditorFrom(state);
    BlazeCommandRunConfigurationCommonState readState =
        new BlazeCommandRunConfigurationCommonState(Blaze.getBuildSystemName(getProject()));
    editor.applyEditorTo(readState);

    assertThat(readState.getCommandState().getCommand())
        .isEqualTo(state.getCommandState().getCommand());
    assertThat(readState.getTestFilterState().getTestFilter())
        .isEqualTo(state.getTestFilterState().getTestFilter());
    assertThat(readState.getExeFlagsState().getRawFlags())
        .isEqualTo(state.getExeFlagsState().getRawFlags());
    assertThat(readState.getUserEnvVarsState().getData().getEnvs())
        .isEqualTo(state.getUserEnvVarsState().getData().getEnvs());
  }

  @Test
  public void editorApplyToAndResetFromShouldHandleNulls() throws Exception {
    RunConfigurationStateEditor editor = state.getEditor(getProject());

    editor.resetEditorFrom(state);
    BlazeCommandRunConfigurationCommonState readState =
        new BlazeCommandRunConfigurationCommonState(Blaze.getBuildSystemName(getProject()));
    editor.applyEditorTo(readState);

    assertThat(readState.getCommandState().getCommand())
        .isEqualTo(state.getCommandState().getCommand());
    assertThat(readState.getTestFilterState().getTestFilter())
        .isEqualTo(state.getTestFilterState().getTestFilter());
    assertThat(readState.getExeFlagsState().getRawFlags())
        .isEqualTo(state.getExeFlagsState().getRawFlags());
    assertThat(readState.getUserEnvVarsState().getData().getEnvs())
        .isEqualTo(state.getUserEnvVarsState().getData().getEnvs());
  }

  @Test
  public void legacyBlazeUserFlagXmlMigratesToTestFilter() throws Exception {
    Element element = new Element("blaze-settings");
    element.addContent(new Element("blaze-user-flag").setText("--test_filter=Foo#bar"));
    element.addContent(new Element("blaze-user-flag").setText("--some_other_flag"));

    state.readExternal(element);

    assertThat(state.getTestFilterState().getTestFilter()).isEqualTo("Foo#bar");
    assertThat(state.getLegacyUserFlags()).containsExactly("--some_other_flag");
    assertThat(element.getChildren("blaze-user-flag")).isEmpty();
  }

  @Test
  public void legacyBlazeUserFlagTestArgsMigrateToExeFlags() throws Exception {
    Element element = new Element("blaze-settings");
    element.addContent(new Element("blaze-user-flag").setText("--test_arg=foo"));
    element.addContent(new Element("blaze-user-flag").setText("--test_arg=bar"));
    element.addContent(new Element("blaze-user-flag").setText("--some_other_flag"));

    state.readExternal(element);

    assertThat(state.getExeFlagsState().getRawFlags()).containsExactly("foo", "bar").inOrder();
    assertThat(state.getLegacyUserFlags()).containsExactly("--some_other_flag");
    assertThat(element.getChildren("blaze-user-flag")).isEmpty();
  }

  @Test
  public void legacyTestArgsAreMergedWithExistingExeFlags() throws Exception {
    Element element = new Element("blaze-settings");
    element.addContent(new Element("blaze-user-exe-flag").setText("--existing"));
    element.addContent(new Element("blaze-user-flag").setText("--test_arg=migrated"));

    state.readExternal(element);

    assertThat(state.getExeFlagsState().getRawFlags())
        .containsExactly("--existing", "migrated")
        .inOrder();
  }

  @Test
  public void legacyXmlWithMultipleTestFilterFlagsKeepsLastOne() throws Exception {
    Element element = new Element("blaze-settings");
    element.addContent(new Element("blaze-user-flag").setText("--test_filter=Foo"));
    element.addContent(new Element("blaze-user-flag").setText("--test_filter=Bar"));

    state.readExternal(element);

    assertThat(state.getTestFilterState().getTestFilter()).isEqualTo("Bar");
    assertThat(state.getLegacyUserFlags()).isEmpty();
  }

  @Test
  public void legacyQuotedTestFilterIsShellDecoded() throws Exception {
    Element element = new Element("blaze-settings");
    element.addContent(new Element("blaze-user-flag").setText("--test_filter=\"Foo Bar\""));

    state.readExternal(element);

    assertThat(state.getTestFilterState().getTestFilter()).isEqualTo("Foo Bar");
    assertThat(state.getLegacyUserFlags()).isEmpty();
  }

  @Test
  public void legacyMigrationIgnoresBlankAndEmptyEntries() throws Exception {
    Element element = new Element("blaze-settings");
    element.addContent(new Element("blaze-user-flag").setText(""));
    element.addContent(new Element("blaze-user-flag").setText("   "));
    element.addContent(new Element("blaze-user-flag").setText("--real_flag"));

    state.readExternal(element);

    assertThat(state.getLegacyUserFlags()).containsExactly("--real_flag");
  }

  @Test
  public void legacyMigrationIsNoOpWhenNoLegacyChildrenPresent() throws Exception {
    Element element = new Element("blaze-settings");

    state.readExternal(element);

    assertThat(state.getTestFilterState().getTestFilter()).isNull();
    assertThat(state.getExeFlagsState().getRawFlags()).isEmpty();
    assertThat(state.getLegacyUserFlags()).isEmpty();
  }

  @Test
  public void clearLegacyUserFlagsRemovesThem() throws Exception {
    Element element = new Element("blaze-settings");
    element.addContent(new Element("blaze-user-flag").setText("--some_flag"));

    state.readExternal(element);
    assertThat(state.getLegacyUserFlags()).containsExactly("--some_flag");

    state.clearLegacyUserFlags();
    assertThat(state.getLegacyUserFlags()).isEmpty();
  }
}
