/*
 * Copyright 2018 The Bazel Authors. All rights reserved.
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

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import com.google.idea.testing.IntellijRule;
import com.intellij.util.SystemProperties;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link FeatureRolloutExperiment}. */
@RunWith(JUnit4.class)
public class FeatureRolloutExperimentTest {

  @Rule public IntellijRule intellij = new IntellijRule();

  private MockExperimentService experimentService;
  private FeatureRolloutExperiment rolloutExperiment;

  @Before
  public void setUp() {
    InternalDevFlag.markUserAsInternalDev(false);

    experimentService = new MockExperimentService();
    intellij.registerApplicationService(ExperimentService.class, experimentService);

    rolloutExperiment = new FeatureRolloutExperiment("feature.name.for.canary.rollout");
  }

  @After
  public void tearDown() {
    SystemProperties.setTestUserName(null); // Reset to real username
  }

  private void setFeatureRolloutPercentage(int percentage) {
    experimentService.setFeatureRolloutExperiment(rolloutExperiment, percentage);
  }

  @Test
  public void testUserHashesInCorrectRange() {
    List<String> usernames =
        Stream.generate(FeatureRolloutExperimentTest::generateUsername)
            .limit(10000)
            .collect(Collectors.toList());

    for (String username : usernames) {
      int percentage = rolloutExperiment.getUserHash(username);
      assertWithMessage(username).that(percentage).isLessThan(100);
      assertWithMessage(username).that(percentage).isAtLeast(0);
    }
  }

  @Test
  public void testUserHashIsSameForEqualUsernameAndExperiment() {
    rolloutExperiment = new FeatureRolloutExperiment("rollout.experiment.one");
    FeatureRolloutExperiment equalExperiment =
        new FeatureRolloutExperiment("rollout.experiment.one");

    List<String> usernames =
        Stream.generate(FeatureRolloutExperimentTest::generateUsername)
            .limit(10000)
            .collect(Collectors.toList());

    for (String username : usernames) {
      int percentage = rolloutExperiment.getUserHash(username);
      assertWithMessage(username)
          .that(percentage)
          .isEqualTo(rolloutExperiment.getUserHash(username));
      assertWithMessage(username).that(percentage).isEqualTo(equalExperiment.getUserHash(username));
    }
  }

  @Test
  public void testUserHashForSameUserDependsOnExperimentKey() {
    rolloutExperiment = new FeatureRolloutExperiment("rollout.experiment.one");
    FeatureRolloutExperiment differentExperiment =
        new FeatureRolloutExperiment("rollout.experiment.two");

    List<String> usernames =
        Stream.generate(FeatureRolloutExperimentTest::generateUsername)
            .limit(10000)
            .collect(Collectors.toList());

    long differenceCount =
        usernames.stream()
            .filter(
                username ->
                    rolloutExperiment.getUserHash(username)
                        != differentExperiment.getUserHash(username))
            .count();
    assertThat(differenceCount).isAtLeast(9800L);
  }

  /**
   * Test that for some set of 'random' usernames, approximately the expected proportion of users
   * have the feature enabled.
   */
  @Test
  public void testRolloutPercentageApproximatelyCorrect() {
    List<String> usernames =
        Stream.generate(FeatureRolloutExperimentTest::generateUsername)
            .limit(10000)
            .collect(Collectors.toList());

    setFeatureRolloutPercentage(0);
    assertThat(usernames.stream().noneMatch(this::isEnabled)).isTrue();

    setFeatureRolloutPercentage(100);
    assertThat(usernames.stream().allMatch(this::isEnabled)).isTrue();

    setFeatureRolloutPercentage(2);
    long matchCount = usernames.stream().filter(this::isEnabled).count();
    assertThat(matchCount).isAtLeast(150L);
    assertThat(matchCount).isAtMost(250L);

    setFeatureRolloutPercentage(20);
    matchCount = usernames.stream().filter(this::isEnabled).count();
    assertThat(matchCount).isAtLeast(1900L);
    assertThat(matchCount).isAtMost(2100L);

    setFeatureRolloutPercentage(50);
    matchCount = usernames.stream().filter(this::isEnabled).count();
    assertThat(matchCount).isAtLeast(4900L);
    assertThat(matchCount).isAtMost(5100L);
  }

  @Test
  public void testAlwaysDisabledIfInvalidRolloutPercentage() {
    List<String> usernames =
        Stream.generate(FeatureRolloutExperimentTest::generateUsername)
            .limit(10000)
            .collect(Collectors.toList());

    setFeatureRolloutPercentage(-1);
    assertThat(usernames.stream().noneMatch(this::isEnabled)).isTrue();

    setFeatureRolloutPercentage(101);
    assertThat(usernames.stream().noneMatch(this::isEnabled)).isTrue();

    setFeatureRolloutPercentage(200);
    assertThat(usernames.stream().noneMatch(this::isEnabled)).isTrue();
  }

  @Test
  public void testAlwaysEnabledForDevs() {
    List<String> usernames =
        Stream.generate(FeatureRolloutExperimentTest::generateUsername)
            .limit(10000)
            .collect(Collectors.toList());
    InternalDevFlag.markUserAsInternalDev(true);

    setFeatureRolloutPercentage(0);
    assertThat(usernames.stream().allMatch(this::isEnabled)).isTrue();

    setFeatureRolloutPercentage(20);
    assertThat(usernames.stream().allMatch(this::isEnabled)).isTrue();

    setFeatureRolloutPercentage(100);
    assertThat(usernames.stream().allMatch(this::isEnabled)).isTrue();

    setFeatureRolloutPercentage(-1);
    assertThat(usernames.stream().allMatch(this::isEnabled)).isTrue();
  }

  @Test
  public void testNotAlwaysEnabledWhenDevMarkerDisabled() {
    List<String> usernames =
        Stream.generate(FeatureRolloutExperimentTest::generateUsername)
            .limit(10000)
            .collect(Collectors.toList());
    InternalDevFlag.markUserAsInternalDev(true);
    experimentService.setExperiment(InternalDevFlag.disableInternalDevMarker, true);

    setFeatureRolloutPercentage(0);
    assertThat(usernames.stream().noneMatch(this::isEnabled)).isTrue();

    setFeatureRolloutPercentage(20);
    long matchCount = usernames.stream().filter(this::isEnabled).count();
    assertThat(matchCount).isAtLeast(1900L);
    assertThat(matchCount).isAtMost(2100L);

    setFeatureRolloutPercentage(100);
    assertThat(usernames.stream().allMatch(this::isEnabled)).isTrue();

    setFeatureRolloutPercentage(-1);
    assertThat(usernames.stream().noneMatch(this::isEnabled)).isTrue();
  }

  private boolean isEnabled(String username) {
    SystemProperties.setTestUserName(username);
    return rolloutExperiment.isEnabled();
  }

  private static final Random random = new Random(12345);

  /** Generates a string of lower-case letters, between 1 and 10 characters in length. */
  private static String generateUsername() {
    int length = Math.round(random.nextFloat() * 9 + 1);
    char diff = 'z' - 'a';
    StringBuilder builder = new StringBuilder(length);
    for (int i = 0; i < length; i++) {
      builder.append((char) ('a' + random.nextFloat() * diff));
    }
    return builder.toString();
  }
}
