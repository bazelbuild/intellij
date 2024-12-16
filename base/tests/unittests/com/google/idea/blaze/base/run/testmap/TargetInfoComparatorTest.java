/*
 * Copyright 2024 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.run.testmap;

import static com.google.common.truth.Truth.assertThat;

import com.google.idea.blaze.base.BlazeTestCase;
import com.google.idea.blaze.base.dependencies.TargetInfo;
import com.google.idea.blaze.base.model.primitives.GenericBlazeRules;
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.model.primitives.Label;
import com.intellij.openapi.extensions.impl.ExtensionPointImpl;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class TargetInfoComparatorTest extends BlazeTestCase {

  private TargetInfoComparator comparator = new TargetInfoComparator();

  @Override
  protected void initTest(Container applicationServices, Container projectServices) {
    super.initTest(applicationServices, projectServices);
    ExtensionPointImpl<Kind.Provider> kindProvider =
        registerExtensionPoint(Kind.Provider.EP_NAME, Kind.Provider.class);
    kindProvider.registerExtension(new GenericBlazeRules());
    applicationServices.register(Kind.ApplicationState.class, new Kind.ApplicationState());
  }

  /**
   * <p>In this case the `zzz` kindString will not resolve to a known instance of
   * {@link com.google.idea.blaze.base.model.primitives.Kind} and so it will come last.</p>
   */

  @Test
  public void testNoKind() {
    List<TargetInfo> targetInfos = List.of(
        TargetInfo.builder(Label.create("//project:aaa"), "zzz").build(),
        TargetInfo.builder(Label.create("//project:bbb"), "sh_test").build()
    );

    List<TargetInfo> sortedTargetInfos = targetInfos
        .stream()
        .sorted(comparator)
        .collect(Collectors.toUnmodifiableList());

    // expecting that the first one will be the one that has the Kind.
    assertThat(sortedTargetInfos.get(0).getLabel()).isEqualTo(Label.create("//project:bbb"));
    assertThat(sortedTargetInfos.get(1).getLabel()).isEqualTo(Label.create("//project:aaa"));
  }

  /**
   * <p>In this case the `_transition_` will be stopped on the `_transition_py_test` and so
   * both {@link TargetInfo}s will be having the
   * {@link com.google.idea.blaze.base.model.primitives.Kind} of `py_test`. This means that the
   * sorting will make the `_aaa` last as it starts with an underscore.</p>
   */
  @Test
  public void testUnderscoreOnLabelTargetName() {
    List<TargetInfo> targetInfos = List.of(
        TargetInfo.builder(Label.create("//project:_aaa"), "sh_test").build(),
        TargetInfo.builder(Label.create("//project:bbb"), "_transition_sh_test").build()
    );

    List<TargetInfo> sortedTargetInfos = targetInfos
        .stream()
        .sorted(comparator)
        .collect(Collectors.toUnmodifiableList());

    // expecting that the first one will be the one that has the Kind.
    assertThat(sortedTargetInfos.get(0).getLabel()).isEqualTo(Label.create("//project:bbb"));
    assertThat(sortedTargetInfos.get(1).getLabel()).isEqualTo(Label.create("//project:_aaa"));
  }

  /**
   * <p>As there is no missing {@link com.google.idea.blaze.base.model.primitives.Kind}
   * nor an underscore-prefixed </p>
   */
  @Test
  public void testByLabelName() {
    List<TargetInfo> targetInfos = List.of(
        TargetInfo.builder(Label.create("//project:ddd"), "sh_test").build(),
        TargetInfo.builder(Label.create("//project:aaa"), "sh_test").build()
    );

    List<TargetInfo> sortedTargetInfos = targetInfos
        .stream()
        .sorted(comparator)
        .collect(Collectors.toUnmodifiableList());

    // expecting that the first one will be the one that has the Kind.
    assertThat(sortedTargetInfos.get(0).getLabel()).isEqualTo(Label.create("//project:aaa"));
    assertThat(sortedTargetInfos.get(1).getLabel()).isEqualTo(Label.create("//project:ddd"));
  }

}
