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
package com.google.idea.blaze.java.run.fastbuild;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.model.primitives.Label;
import com.intellij.execution.ExecutionException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link LocationSubstitution}. */
@RunWith(JUnit4.class)
public class LocationSubstitutionTest {

  private static final Label SOURCE_TARGET =
      Label.create("//java/com/google/devtools/intellij/g3plugins:g3plugins");

  @Test
  public void testBasicSubstitution() throws ExecutionException {
    ImmutableMap<Label, ImmutableSet<ArtifactLocation>> data =
        ImmutableMap.of(
            Label.create("//test/location:location"),
            ImmutableSet.of(
                ArtifactLocation.builder().setRelativePath("test/location.txt").build()));

    String result =
        LocationSubstitution.replaceLocations(
            "foo$(location //test/location)bar", SOURCE_TARGET, data);

    assertThat(result).isEqualTo("footest/location.txtbar");
  }

  @Test
  public void testBasicSubstitution_targetWithColon() throws ExecutionException {
    ImmutableMap<Label, ImmutableSet<ArtifactLocation>> data =
        ImmutableMap.of(
            Label.create("//test/location:hello"),
            ImmutableSet.of(
                ArtifactLocation.builder().setRelativePath("test/location.txt").build()));

    String result =
        LocationSubstitution.replaceLocations(
            "foo$(location //test/location:hello)bar", SOURCE_TARGET, data);

    assertThat(result).isEqualTo("footest/location.txtbar");
  }

  @Test
  public void testBasicSubstitution_relativeTarget() throws ExecutionException {
    ImmutableMap<Label, ImmutableSet<ArtifactLocation>> data =
        ImmutableMap.of(
            SOURCE_TARGET.withTargetName("hello"),
            ImmutableSet.of(
                ArtifactLocation.builder().setRelativePath("test/location.txt").build()));

    String result =
        LocationSubstitution.replaceLocations("foo$(location :hello)bar", SOURCE_TARGET, data);

    assertThat(result).isEqualTo("footest/location.txtbar");
  }

  @Test
  public void testBasicSubstitution_relativeTargetWithoutColon() throws ExecutionException {
    ImmutableMap<Label, ImmutableSet<ArtifactLocation>> data =
        ImmutableMap.of(
            SOURCE_TARGET.withTargetName("hello"),
            ImmutableSet.of(
                ArtifactLocation.builder().setRelativePath("test/location.txt").build()));

    String result =
        LocationSubstitution.replaceLocations("foo$(location hello)bar", SOURCE_TARGET, data);

    assertThat(result).isEqualTo("footest/location.txtbar");
  }

  @Test
  public void testBasicSubstitution_relativeFilename() throws ExecutionException {
    ImmutableMap<Label, ImmutableSet<ArtifactLocation>> data =
        ImmutableMap.of(
            SOURCE_TARGET.withTargetName("files/hello.txt"),
            ImmutableSet.of(
                ArtifactLocation.builder().setRelativePath("test/location.txt").build()));

    String result =
        LocationSubstitution.replaceLocations(
            "foo$(location files/hello.txt)bar", SOURCE_TARGET, data);

    assertThat(result).isEqualTo("footest/location.txtbar");
  }

  @Test
  public void testDoubleSubstitution() throws ExecutionException {
    ImmutableMap<Label, ImmutableSet<ArtifactLocation>> data =
        ImmutableMap.of(
            Label.create("//first/location:location"),
            ImmutableSet.of(
                ArtifactLocation.builder().setRelativePath("first/location.txt").build()),
            Label.create("//second/location:location"),
            ImmutableSet.of(
                ArtifactLocation.builder().setRelativePath("second/location.zip").build()));
    String result =
        LocationSubstitution.replaceLocations(
            "one $(location //first/location) two $(location //second/location) three",
            SOURCE_TARGET,
            data);

    assertThat(result).isEqualTo("one first/location.txt two second/location.zip three");
  }

  @Test
  public void testLocationsWithSingleArtifact() throws ExecutionException {
    ImmutableMap<Label, ImmutableSet<ArtifactLocation>> data =
        ImmutableMap.of(
            Label.create("//test/location:location"),
            ImmutableSet.of(
                ArtifactLocation.builder().setRelativePath("test/location.txt").build()));

    String result =
        LocationSubstitution.replaceLocations(
            "foo$(locations //test/location)bar", SOURCE_TARGET, data);

    assertThat(result).isEqualTo("footest/location.txtbar");
  }

  @Test
  public void testLocationsWithMultipleArtifacts() throws ExecutionException {
    ImmutableMap<Label, ImmutableSet<ArtifactLocation>> data =
        ImmutableMap.of(
            Label.create("//test/location:location"),
            ImmutableSet.of(
                ArtifactLocation.builder().setRelativePath("test/one.txt").build(),
                ArtifactLocation.builder().setRelativePath("test/two.txt").build()));

    String result =
        LocationSubstitution.replaceLocations(
            "foo$(locations //test/location)bar", SOURCE_TARGET, data);

    assertThat(result).isEqualTo("footest/one.txt test/two.txtbar");
  }

  @Test
  public void testIgnoresWhitespace() {
    ImmutableMap<Label, ImmutableSet<ArtifactLocation>> data = ImmutableMap.of();

    try {
      LocationSubstitution.replaceLocations(
          "foo$(location //test/location)bar", SOURCE_TARGET, data);
      fail("should have thrown ExecutionException");
    } catch (ExecutionException e) {
      assertThat(e.getMessage()).contains("//test/location");
      assertThat(e.getMessage()).contains("'data' attribute");
      assertThat(e.getMessage()).contains(SOURCE_TARGET.toString());
    }
  }

  @Test
  public void testMultipleArtifacts() {
    ImmutableMap<Label, ImmutableSet<ArtifactLocation>> data =
        ImmutableMap.of(
            Label.create("//test/location:location"),
            ImmutableSet.of(
                ArtifactLocation.builder().setRelativePath("test/location.txt").build(),
                ArtifactLocation.builder().setRelativePath("test/location.zip").build()));

    try {
      LocationSubstitution.replaceLocations(
          "foo$(location //test/location)bar", SOURCE_TARGET, data);
      fail("should have thrown ExecutionException");
    } catch (ExecutionException e) {
      assertThat(e.getMessage()).contains("//test/location");
      assertThat(e.getMessage()).contains("multiple outputs");
      assertThat(e.getMessage()).contains(SOURCE_TARGET.toString());
    }
  }

  @Test
  public void testNoArtifacts() {
    ImmutableMap<Label, ImmutableSet<ArtifactLocation>> data =
        ImmutableMap.of(Label.create("//test/location:location"), ImmutableSet.of());

    try {
      LocationSubstitution.replaceLocations(
          "foo$(location //test/location)bar", SOURCE_TARGET, data);
      fail("should have thrown ExecutionException");
    } catch (ExecutionException e) {
      assertThat(e.getMessage()).contains("//test/location");
      assertThat(e.getMessage()).contains("no outputs");
      assertThat(e.getMessage()).contains(SOURCE_TARGET.toString());
    }
  }
}
