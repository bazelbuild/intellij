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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

import com.google.idea.blaze.base.BlazeTestCase;
import com.google.idea.common.experiments.ExperimentService;
import com.google.idea.common.experiments.MockExperimentService;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link com.google.idea.blaze.base.model.primitives.TargetExpressionFactory}. */
@RunWith(JUnit4.class)
public class TargetExpressionTest extends BlazeTestCase {
  @Override
  protected void initTest(
      @NotNull Container applicationServices, @NotNull Container projectServices) {
    super.initTest(applicationServices, projectServices);
    applicationServices.register(ExperimentService.class, new MockExperimentService());
  }

  @Test
  public void validLabelShouldYieldLabel() {
    TargetExpression target = TargetExpression.fromString("//package:rule");
    assertThat(target).isInstanceOf(Label.class);
  }

  @Test
  public void globExpressionShouldYieldGeneralTargetExpression() {
    TargetExpression target = TargetExpression.fromString("//package/...");
    assertThat(target.getClass()).isSameAs(TargetExpression.class);
  }

  @Test
  public void emptyExpressionShouldThrow() {
    try {
      TargetExpression.fromString("");
      fail("Empty expressions should not be allowed.");
    } catch (IllegalArgumentException expected) {
    }
  }
}
