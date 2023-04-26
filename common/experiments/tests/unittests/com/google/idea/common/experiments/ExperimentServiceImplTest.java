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
import com.google.idea.testing.IntellijRule;
import java.util.HashMap;
import java.util.Map;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link ExperimentServiceImpl}. */
@RunWith(JUnit4.class)
public class ExperimentServiceImplTest {

  private static final BoolExperiment BOOL_EXPERIMENT = new BoolExperiment("test.property", false);
  private static final StringExperiment STRING_EXPERIMENT = new StringExperiment("test.property");
  private static final IntExperiment INT_EXPERIMENT = new IntExperiment("test.property", 0);

  @Rule public IntellijRule intellij = new IntellijRule();

  @Test
  public void testEmptyLoadersList() {
    ExperimentService experimentService = new ExperimentServiceImpl(new ExperimentLoader[] {});
    assertThat(experimentService.getExperiment(BOOL_EXPERIMENT, false)).isFalse();
  }

  @Test
  public void testBooleanPropertyTrue() {
    ExperimentService experimentService =
        new ExperimentServiceImpl(new MapExperimentLoader(BOOL_EXPERIMENT.getKey(), "1"));
    assertThat(experimentService.getExperiment(BOOL_EXPERIMENT, false)).isTrue();
  }

  @Test
  public void testBooleanPropertyFalse() {
    ExperimentService experimentService =
        new ExperimentServiceImpl(new MapExperimentLoader(BOOL_EXPERIMENT.getKey(), "0"));
    assertThat(experimentService.getExperiment(BOOL_EXPERIMENT, true)).isFalse();
  }

  @Test
  public void testBooleanPropertyReturnsDefaultWhenMissing() {
    ExperimentService experimentService = new ExperimentServiceImpl(new MapExperimentLoader());
    assertThat(experimentService.getExperiment(BOOL_EXPERIMENT, true)).isTrue();
  }

  @Test
  public void testStringProperty() {
    ExperimentService experimentService =
        new ExperimentServiceImpl(new MapExperimentLoader(STRING_EXPERIMENT.getKey(), "hi"));
    assertThat(experimentService.getExperimentString(STRING_EXPERIMENT, null)).isEqualTo("hi");
  }

  @Test
  public void testStringPropertyReturnsDefaultWhenMissing() {
    ExperimentService experimentService = new ExperimentServiceImpl(new MapExperimentLoader());
    assertThat(experimentService.getExperimentString(STRING_EXPERIMENT, "bye")).isEqualTo("bye");
  }

  @Test
  public void testFirstLoaderOverridesSecond() {
    ExperimentService experimentService =
        new ExperimentServiceImpl(
            new MapExperimentLoader(BOOL_EXPERIMENT.getKey(), "1"),
            new MapExperimentLoader(BOOL_EXPERIMENT.getKey(), "0"));
    assertThat(experimentService.getExperiment(BOOL_EXPERIMENT, false)).isTrue();
  }

  @Test
  public void testOnlyInSecondLoader() {
    ExperimentService experimentService =
        new ExperimentServiceImpl(
            new MapExperimentLoader(), new MapExperimentLoader(BOOL_EXPERIMENT.getKey(), "1"));
    assertThat(experimentService.getExperiment(BOOL_EXPERIMENT, false)).isTrue();
  }

  @Test
  public void testIntProperty() {
    ExperimentService experimentService =
        new ExperimentServiceImpl(new MapExperimentLoader(INT_EXPERIMENT.getKey(), "10"));
    assertThat(experimentService.getExperimentInt(INT_EXPERIMENT, 0)).isEqualTo(10);
  }

  @Test
  public void testIntPropertyDefaultValue() {
    ExperimentService experimentService = new ExperimentServiceImpl(new MapExperimentLoader());
    assertThat(experimentService.getExperimentInt(INT_EXPERIMENT, 100)).isEqualTo(100);
  }

  @Test
  public void testIntPropertyThatDoesntParseReturnsDefaultValue() {
    ExperimentService experimentService =
        new ExperimentServiceImpl(new MapExperimentLoader(INT_EXPERIMENT.getKey(), "hello"));
    assertThat(experimentService.getExperimentInt(INT_EXPERIMENT, 111)).isEqualTo(111);
  }

  @Test
  public void testDataIsReloadedAgainWhenLeavingAScope() {
    MapExperimentLoader experimentLoader = new MapExperimentLoader();
    ExperimentService experimentService = new ExperimentServiceImpl(experimentLoader);
    experimentService.startExperimentScope();
    assertThat(experimentService.getExperimentString(STRING_EXPERIMENT, "default"))
        .isEqualTo("default");
    experimentLoader.map.put(STRING_EXPERIMENT.getKey(), "hello");
    experimentService.endExperimentScope();
    assertThat(experimentService.getExperimentString(STRING_EXPERIMENT, "default"))
        .isEqualTo("hello");
  }

  @Test
  public void testEnterTwoScopesButOnlyLeaveOne() {
    MapExperimentLoader experimentLoader = new MapExperimentLoader();
    ExperimentService experimentService = new ExperimentServiceImpl(experimentLoader);
    experimentService.startExperimentScope();
    assertThat(experimentService.getExperimentString(STRING_EXPERIMENT, "default"))
        .isEqualTo("default");
    experimentService.startExperimentScope();
    experimentService.endExperimentScope();
    experimentLoader.map.put(STRING_EXPERIMENT.getKey(), "hello");
    assertThat(experimentService.getExperimentString(STRING_EXPERIMENT, "default"))
        .isEqualTo("default");
  }

  @Test
  public void testEnterAndLeaveTwoScopes() {
    MapExperimentLoader experimentLoader = new MapExperimentLoader();
    ExperimentService experimentService = new ExperimentServiceImpl(experimentLoader);
    experimentService.startExperimentScope();
    assertThat(experimentService.getExperimentString(STRING_EXPERIMENT, "default"))
        .isEqualTo("default");
    experimentService.startExperimentScope();
    experimentLoader.map.put(STRING_EXPERIMENT.getKey(), "hello");
    experimentService.endExperimentScope();
    experimentService.endExperimentScope();
    assertThat(experimentService.getExperimentString(STRING_EXPERIMENT, "default"))
        .isEqualTo("hello");
  }

  @Test
  public void testLeaveAndEnterRefreshes() {
    MapExperimentLoader experimentLoader = new MapExperimentLoader();
    experimentLoader.map.put(STRING_EXPERIMENT.getKey(), "one");
    ExperimentService experimentService = new ExperimentServiceImpl(experimentLoader);
    experimentService.startExperimentScope();
    assertThat(experimentService.getExperimentString(STRING_EXPERIMENT, "default"))
        .isEqualTo("one");
    experimentLoader.map.put(STRING_EXPERIMENT.getKey(), "two");
    experimentService.endExperimentScope();
    experimentService.startExperimentScope();
    assertThat(experimentService.getExperimentString(STRING_EXPERIMENT, "default"))
        .isEqualTo("two");
  }

  private static class MapExperimentLoader implements ExperimentLoader {

    private final Map<String, String> map;

    private MapExperimentLoader(String... keysAndValues) {
      checkState(keysAndValues.length % 2 == 0);
      map = new HashMap<>();
      for (int i = 0; i < keysAndValues.length; i += 2) {
        map.put(keysAndValues[i], keysAndValues[i + 1]);
      }
    }

    @Override
    public ImmutableMap<String, String> getExperiments() {
      return ImmutableMap.copyOf(map);
    }

    @Override
    public void initialize() {}
  }
}
