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
package com.google.idea.blaze.base.experiments;

import com.google.common.collect.ImmutableMap;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import java.util.Map;

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.truth.Truth.assertThat;

/**
 * Tests for {@link ExperimentServiceImpl}.
 */
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

  private static class MapExperimentLoader implements ExperimentLoader {

    private final Map<String, String> map;

    private MapExperimentLoader(String... keysAndValues) {
      checkState(keysAndValues.length % 2 == 0);
      ImmutableMap.Builder<String, String> mapBuilder = ImmutableMap.builder();
      for (int i = 0; i < keysAndValues.length; i += 2) {
        mapBuilder.put(keysAndValues[i], keysAndValues[i + 1]);
      }
      map = mapBuilder.build();
    }

    @Override
    public Map<String, String> getExperiments() {
      return map;
    }
  }
}
