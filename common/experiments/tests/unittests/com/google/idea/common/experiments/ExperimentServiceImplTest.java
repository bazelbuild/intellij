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
package com.google.idea.common.experiments;

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableMap;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link ExperimentServiceImpl}. */
@RunWith(JUnit4.class)
public class ExperimentServiceImplTest {

  @Test
  public void testBooleanPropertyTrue() {
    ExperimentService experimentService =
        new ExperimentServiceImpl(new MapExperimentLoader("test.property", "1"));
    assertThat(experimentService.getExperiment("test.property", false)).isTrue();
  }

  @Test
  public void testBooleanPropertyFalse() {
    ExperimentService experimentService =
        new ExperimentServiceImpl(new MapExperimentLoader("test.property", "0"));
    assertThat(experimentService.getExperiment("test.property", true)).isFalse();
  }

  @Test
  public void testBooleanPropertyReturnsDefaultWhenMissing() {
    ExperimentService experimentService = new ExperimentServiceImpl(new MapExperimentLoader());
    assertThat(experimentService.getExperiment("test.notthere", true)).isTrue();
  }

  @Test
  public void testStringProperty() {
    ExperimentService experimentService =
        new ExperimentServiceImpl(new MapExperimentLoader("test.property", "hi"));
    assertThat(experimentService.getExperimentString("test.property", null)).isEqualTo("hi");
  }

  @Test
  public void testStringPropertyReturnsDefaultWhenMissing() {
    ExperimentService experimentService = new ExperimentServiceImpl(new MapExperimentLoader());
    assertThat(experimentService.getExperimentString("test.property", "bye")).isEqualTo("bye");
  }

  @Test
  public void testFirstLoaderOverridesSecond() {
    ExperimentService experimentService =
        new ExperimentServiceImpl(
            new MapExperimentLoader("test.property", "1"),
            new MapExperimentLoader("test.property", "0"));
    assertThat(experimentService.getExperiment("test.property", false)).isTrue();
  }

  @Test
  public void testOnlyInSecondLoader() {
    ExperimentService experimentService =
        new ExperimentServiceImpl(
            new MapExperimentLoader(), new MapExperimentLoader("test.property", "1"));
    assertThat(experimentService.getExperiment("test.property", false)).isTrue();
  }

  @Test
  public void testIntProperty() {
    ExperimentService experimentService =
        new ExperimentServiceImpl(new MapExperimentLoader("test.property", "10"));
    assertThat(experimentService.getExperimentInt("test.property", 0)).isEqualTo(10);
  }

  @Test
  public void testIntPropertyDefaultValue() {
    ExperimentService experimentService = new ExperimentServiceImpl(new MapExperimentLoader());
    assertThat(experimentService.getExperimentInt("test.property", 100)).isEqualTo(100);
  }

  @Test
  public void testIntPropertyThatDoesntParseReturnsDefaultValue() {
    ExperimentService experimentService =
        new ExperimentServiceImpl(new MapExperimentLoader("test.property", "hello"));
    assertThat(experimentService.getExperimentInt("test.property", 111)).isEqualTo(111);
  }

  @Test
  public void testDataIsReloadedWhenNotInAScope() throws Exception {
    MapExperimentLoader experimentLoader = new MapExperimentLoader();
    ExperimentService experimentService = new ExperimentServiceImpl(experimentLoader);
    assertThat(experimentService.getExperimentString("test.property", "default"))
        .isEqualTo("default");
    experimentLoader.map.put("test.property", "hello");
    assertThat(experimentService.getExperimentString("test.property", "default"))
        .isEqualTo("hello");
  }

  @Test
  public void testDataIsFrozenWheninAScope() throws Exception {
    MapExperimentLoader experimentLoader = new MapExperimentLoader();
    ExperimentService experimentService = new ExperimentServiceImpl(experimentLoader);
    experimentService.startExperimentScope();
    assertThat(experimentService.getExperimentString("test.property", "default"))
        .isEqualTo("default");
    experimentLoader.map.put("test.property", "hello");
    assertThat(experimentService.getExperimentString("test.property", "default"))
        .isEqualTo("default");
  }

  @Test
  public void testDataIsReloadedAgainWhenLeavingAScope() throws Exception {
    MapExperimentLoader experimentLoader = new MapExperimentLoader();
    ExperimentService experimentService = new ExperimentServiceImpl(experimentLoader);
    experimentService.startExperimentScope();
    assertThat(experimentService.getExperimentString("test.property", "default"))
        .isEqualTo("default");
    experimentLoader.map.put("test.property", "hello");
    experimentService.endExperimentScope();
    assertThat(experimentService.getExperimentString("test.property", "default"))
        .isEqualTo("hello");
  }

  @Test
  public void testEnterTwoScopesButOnlyLeaveOne() throws Exception {
    MapExperimentLoader experimentLoader = new MapExperimentLoader();
    ExperimentService experimentService = new ExperimentServiceImpl(experimentLoader);
    experimentService.startExperimentScope();
    assertThat(experimentService.getExperimentString("test.property", "default"))
        .isEqualTo("default");
    experimentService.startExperimentScope();
    experimentService.endExperimentScope();
    experimentLoader.map.put("test.property", "hello");
    assertThat(experimentService.getExperimentString("test.property", "default"))
        .isEqualTo("default");
  }

  @Test
  public void testEnterAndLeaveTwoScopes() throws Exception {
    MapExperimentLoader experimentLoader = new MapExperimentLoader();
    ExperimentService experimentService = new ExperimentServiceImpl(experimentLoader);
    experimentService.startExperimentScope();
    assertThat(experimentService.getExperimentString("test.property", "default"))
        .isEqualTo("default");
    experimentService.startExperimentScope();
    experimentService.endExperimentScope();
    experimentService.endExperimentScope();
    experimentLoader.map.put("test.property", "hello");
    assertThat(experimentService.getExperimentString("test.property", "default"))
        .isEqualTo("hello");
  }

  @Test
  public void testLeaveAndEnterRefreshes() throws Exception {
    MapExperimentLoader experimentLoader = new MapExperimentLoader();
    experimentLoader.map.put("test.property", "one");
    ExperimentService experimentService = new ExperimentServiceImpl(experimentLoader);
    experimentService.startExperimentScope();
    assertThat(experimentService.getExperimentString("test.property", "default")).isEqualTo("one");
    experimentLoader.map.put("test.property", "two");
    experimentService.endExperimentScope();
    experimentService.startExperimentScope();
    assertThat(experimentService.getExperimentString("test.property", "default")).isEqualTo("two");
  }

  private static class MapExperimentLoader extends HashingExperimentLoader {

    private final Map<String, String> map;

    private MapExperimentLoader(String... keysAndValues) {
      checkState(keysAndValues.length % 2 == 0);
      map = new HashMap<>();
      for (int i = 0; i < keysAndValues.length; i += 2) {
        map.put(keysAndValues[i], keysAndValues[i + 1]);
      }
    }

    @Override
    public ImmutableMap<String, String> getUnhashedExperiments() {
      return ImmutableMap.copyOf(map);
    }

    @Override
    public void initialize() {}
  }
}
