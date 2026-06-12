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

import org.jdom.Element;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link TestFilterState}. */
@RunWith(JUnit4.class)
public class TestFilterStateTest {

  @Test
  public void testFilterFlagIsNullWhenUnset() {
    TestFilterState state = new TestFilterState();
    assertThat(state.getTestFilter()).isNull();
    assertThat(state.getTestFilterFlag()).isNull();
  }

  @Test
  public void blankTestFilterIsNormalisedToNull() {
    TestFilterState state = new TestFilterState();
    state.setTestFilter("");
    assertThat(state.getTestFilter()).isNull();
    assertThat(state.getTestFilterFlag()).isNull();
  }

  @Test
  public void simpleTestFilterRendersAsBareFlag() {
    TestFilterState state = new TestFilterState();
    state.setTestFilter("Foo#bar");
    assertThat(state.getTestFilterFlag()).isEqualTo("--test_filter=Foo#bar");
  }

  @Test
  public void testFilterWithWhitespaceIsShellQuoted() {
    TestFilterState state = new TestFilterState();
    state.setTestFilter("Foo Bar");
    assertThat(state.getTestFilterFlag()).isEqualTo("--test_filter=\"Foo Bar\"");
  }

  @Test
  public void readAndWriteRoundTripsThroughXml() {
    TestFilterState source = new TestFilterState();
    source.setTestFilter("ClassName#method");

    Element element = new Element("test");
    source.writeExternal(element);

    TestFilterState target = new TestFilterState();
    target.readExternal(element);
    assertThat(target.getTestFilter()).isEqualTo("ClassName#method");
  }

  @Test
  public void writeOfEmptyStateProducesNoXmlChildren() {
    TestFilterState state = new TestFilterState();
    Element element = new Element("test");
    state.writeExternal(element);
    assertThat(element.getChildren("blaze-test-filter")).isEmpty();
  }

  @Test
  public void readExternalIsNoOpWhenElementHasNoTestFilter() {
    TestFilterState state = new TestFilterState();
    state.setTestFilter("preserved");

    state.readExternal(new Element("test"));
    assertThat(state.getTestFilter()).isEqualTo("preserved");
  }

  @Test
  public void repeatedWriteReplacesPreviousElement() {
    TestFilterState state = new TestFilterState();
    state.setTestFilter("first");

    Element element = new Element("test");
    state.writeExternal(element);
    state.setTestFilter("second");
    state.writeExternal(element);

    assertThat(element.getChildren("blaze-test-filter")).hasSize(1);
    assertThat(element.getChildText("blaze-test-filter")).isEqualTo("second");
  }
}
