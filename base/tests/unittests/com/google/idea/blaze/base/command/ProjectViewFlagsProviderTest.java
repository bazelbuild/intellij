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
package com.google.idea.blaze.base.command;

import static com.google.common.truth.Truth.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ProjectViewFlagsProviderTest {

  private static List<String> filter(String... flags) {
    final var list = new ArrayList<String>(List.of(flags));
    ProjectViewFlagsProvider.removeInfoIncompatibleFlags(list);
    return list;
  }

  @Test
  public void keepsUnrelatedFlags() {
    assertThat(filter("--foo", "--bar=baz")).containsExactly("--foo", "--bar=baz").inOrder();
  }

  @Test
  public void removesInlineValueForm() {
    assertThat(filter("--config=release", "--platforms=//foo:bar")).isEmpty();
  }

  @Test
  public void removesSpaceSeparatedFormWithinSingleElement() {
    assertThat(filter("--config release", "--platforms //foo:bar")).isEmpty();
  }

  @Test
  public void removesBareFlagAndFollowingValueArgument() {
    assertThat(filter("--config", "release", "--foo")).containsExactly("--foo");
    assertThat(filter("--platforms", "//foo:bar", "--foo")).containsExactly("--foo");
  }

  @Test
  public void bareFlagConsumesFollowingArgumentEvenIfItLooksLikeAFlag() {
    assertThat(filter("--config", "--foo", "--keep")).containsExactly("--keep");
  }

  @Test
  public void removesBareFlagAtEndOfList() {
    assertThat(filter("--foo", "--config")).containsExactly("--foo");
  }

  @Test
  public void keepsFlagsThatOnlySharePrefix() {
    assertThat(filter("--config_foo", "--platforms_bar")).containsExactly("--config_foo", "--platforms_bar").inOrder();
  }

  @Test
  public void handlesMixOfFormsAndPreservesOrder() {
    assertThat(filter(
        "--keep_one",
        "--config=debug",
        "--config",
        "fastbuild",
        "--platforms //foo:bar",
        "--keep_two"
    )).containsExactly("--keep_one", "--keep_two").inOrder();
  }
}
