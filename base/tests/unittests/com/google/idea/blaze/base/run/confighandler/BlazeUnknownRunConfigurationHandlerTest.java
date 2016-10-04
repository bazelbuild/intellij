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
package com.google.idea.blaze.base.run.confighandler;

import static com.google.common.truth.Truth.assertThat;

import com.google.idea.blaze.base.BlazeTestCase;
import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration;
import com.google.idea.blaze.base.run.BlazeCommandRunConfigurationType;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.BlazeImportSettings;
import com.google.idea.blaze.base.settings.BlazeImportSettingsManager;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.util.InvalidDataException;
import java.io.StringReader;
import org.jdom.Element;
import org.jdom.input.SAXBuilder;
import org.jdom.output.Format;
import org.jdom.output.XMLOutputter;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link BlazeUnknownRunConfigurationHandler}. */
@RunWith(JUnit4.class)
public class BlazeUnknownRunConfigurationHandlerTest extends BlazeTestCase {
  private static final BlazeImportSettings DUMMY_IMPORT_SETTINGS =
      new BlazeImportSettings("", "", "", "", "", Blaze.BuildSystem.Blaze);

  private final BlazeCommandRunConfigurationType type = new BlazeCommandRunConfigurationType();
  private BlazeCommandRunConfiguration configuration;
  private BlazeUnknownRunConfigurationHandler handler;

  @Override
  protected void initTest(
      @NotNull Container applicationServices, @NotNull Container projectServices) {
    super.initTest(applicationServices, projectServices);

    applicationServices.register(UISettings.class, new UISettings());
    projectServices.register(
        BlazeImportSettingsManager.class, new BlazeImportSettingsManager(project));
    BlazeImportSettingsManager.getInstance(getProject()).setImportSettings(DUMMY_IMPORT_SETTINGS);

    configuration = type.getFactory().createTemplateConfiguration(project);
    handler = new BlazeUnknownRunConfigurationHandler(configuration);
  }

  @Test
  public void readAndWriteShouldPreserveOldContent() throws Exception {
    SAXBuilder saxBuilder = new SAXBuilder();
    XMLOutputter xmlOutputter = new XMLOutputter(Format.getCompactFormat());

    String inputXml =
        "<?xml version=\"1.0\"?>"
            + "<test foo=\"bar\" bar=\"baz\">"
            + "  <child abc=\"def\">"
            + "    <grandchild />"
            + "  </child>"
            + "  <child foo=\"baz\" />"
            + "</test>";
    Element element = saxBuilder.build(new StringReader(inputXml)).getRootElement();
    handler.readExternal(element);

    Element writeElement = new Element("test");
    handler.writeExternal(writeElement);

    assertThat(xmlOutputter.outputString(writeElement))
        .isEqualTo(xmlOutputter.outputString(element));
  }

  @Test
  public void readAndWriteShouldHandleEmptyElements() throws InvalidDataException {
    //<test />
    Element element = new Element("test");
    handler.readExternal(element);

    Element writeElement = new Element("test");
    handler.writeExternal(writeElement);

    assertThat(writeElement.getAttributes()).isEmpty();
    assertThat(writeElement.getChildren()).isEmpty();
  }

  @Test
  public void writeShouldPreserveNewContent() throws Exception {
    SAXBuilder saxBuilder = new SAXBuilder();
    XMLOutputter xmlOutputter = new XMLOutputter(Format.getCompactFormat());

    //<test />
    Element element = new Element("test");
    handler.readExternal(element);

    String newXml =
        "<?xml version=\"1.0\"?>"
            + "<test foo=\"bar\">"
            + "  <child abc=\"def\" />"
            + "  <child />"
            + "</test>";
    Element writeElement = saxBuilder.build(new StringReader(newXml)).getRootElement();
    handler.writeExternal(writeElement);

    Element newElement = saxBuilder.build(new StringReader(newXml)).getRootElement();
    assertThat(xmlOutputter.outputString(writeElement))
        .isEqualTo(xmlOutputter.outputString(newElement));
  }

  @Test
  public void writeShouldMergeAndOverwriteOldContent() throws Exception {
    SAXBuilder saxBuilder = new SAXBuilder();
    XMLOutputter xmlOutputter = new XMLOutputter(Format.getCompactFormat());

    String oldXml =
        "<?xml version=\"1.0\"?>"
            + "<test foo=\"old\" bar=\"old\">"
            + "  <child abc=\"old\">"
            + "    <grandchild />"
            + "  </child>"
            + "  <backup foo=\"baz\" />"
            + "  <backup />"
            + "</test>";
    Element element = saxBuilder.build(new StringReader(oldXml)).getRootElement();
    handler.readExternal(element);

    String newXml =
        "<?xml version=\"1.0\"?>"
            + "<test foo=\"bar\">"
            + "  <child abc=\"def\" />"
            + "  <child />"
            + "</test>";
    Element writeElement = saxBuilder.build(new StringReader(newXml)).getRootElement();
    handler.writeExternal(writeElement);

    String mergedXml =
        "<?xml version=\"1.0\"?>"
            + "<test foo=\"bar\" bar=\"old\">"
            + "  <child abc=\"def\" />"
            + "  <child />"
            + "  <backup foo=\"baz\" />"
            + "  <backup />"
            + "</test>";
    Element mergedElement = saxBuilder.build(new StringReader(mergedXml)).getRootElement();
    assertThat(xmlOutputter.outputString(writeElement))
        .isEqualTo(xmlOutputter.outputString(mergedElement));
  }
}
