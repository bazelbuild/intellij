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
package com.google.idea.blaze.base.model.primitives;

import com.google.idea.blaze.base.BlazeTestCase;
import com.google.idea.blaze.base.experiments.ExperimentService;
import com.google.idea.blaze.base.experiments.MockExperimentService;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import static com.google.common.truth.Truth.assertThat;

public class LabelTest extends BlazeTestCase {

  @Override
  protected void initTest(
    @NotNull Container applicationServices,
    @NotNull Container projectServices) {
    super.initTest(applicationServices, projectServices);
    applicationServices.register(ExperimentService.class, new MockExperimentService());
  }

  @Test
  public void testValidatePackage() {
    // Legal names
    assertThat(Label.validatePackagePath("foo")).isTrue();
    assertThat(Label.validatePackagePath("f")).isTrue();
    assertThat(Label.validatePackagePath("fooBAR")).isTrue();
    assertThat(Label.validatePackagePath("foo/bar")).isTrue();
    assertThat(Label.validatePackagePath("f9oo")).isTrue();
    assertThat(Label.validatePackagePath("f_9oo")).isTrue();
    // This is not advised but is technically legal
    assertThat(Label.validatePackagePath("")).isTrue();

    // Illegal names
    assertThat(Label.validatePackagePath("Foo")).isFalse();
    assertThat(Label.validatePackagePath("foo//bar")).isFalse();
    assertThat(Label.validatePackagePath("foo/")).isFalse();
    assertThat(Label.validatePackagePath("9oo")).isFalse();
  }

  @Test
  public void testValidateLabel() {
    // Valid labels
    assertThat(Label.validate("//foo:bar")).isTrue();
    assertThat(Label.validate("//foo/baz:bar")).isTrue();
    assertThat(Label.validate("//:bar")).isTrue();

    // Invalid labels
    assertThat(Label.validate("//foo")).isFalse();
    assertThat(Label.validate("foo")).isFalse();
    assertThat(Label.validate("foo:bar")).isFalse();
  }
}
