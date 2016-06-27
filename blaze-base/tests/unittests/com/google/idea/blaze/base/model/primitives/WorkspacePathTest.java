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

import com.google.common.collect.Lists;
import com.google.idea.blaze.base.BlazeTestCase;
import com.google.idea.blaze.base.ui.BlazeValidationError;
import org.junit.Test;

import java.util.List;

import static com.google.common.truth.Truth.assertThat;

public class WorkspacePathTest extends BlazeTestCase {

  @Test
  public void testValidation() {
    // Valid workspace paths
    assertThat(WorkspacePath.validate("")).isTrue();
    assertThat(WorkspacePath.validate("foo")).isTrue();
    assertThat(WorkspacePath.validate("foo")).isTrue();
    assertThat(WorkspacePath.validate("foo/bar")).isTrue();
    assertThat(WorkspacePath.validate("foo/bar/baz")).isTrue();

    // Invalid workspace paths
    assertThat(WorkspacePath.validate("/foo")).isFalse();
    assertThat(WorkspacePath.validate("//foo")).isFalse();
    assertThat(WorkspacePath.validate("/")).isFalse();
    assertThat(WorkspacePath.validate("foo/")).isFalse();
    assertThat(WorkspacePath.validate("foo:")).isFalse();
    assertThat(WorkspacePath.validate(":")).isFalse();
    assertThat(WorkspacePath.validate("foo:bar")).isFalse();


    List<BlazeValidationError> errors = Lists.newArrayList();

    WorkspacePath.validate("/foo", errors);
    assertThat(errors.get(0).getError()).isEqualTo("Workspace path may not start with '/': /foo");
    errors.clear();

    WorkspacePath.validate("/", errors);
    assertThat(errors.get(0).getError()).isEqualTo("Workspace path may not start with '/': /");
    errors.clear();

    WorkspacePath.validate("foo/", errors);
    assertThat(errors.get(0).getError()).isEqualTo("Workspace path may not end with '/': foo/");
    errors.clear();

    WorkspacePath.validate("foo:bar", errors);
    assertThat(errors.get(0).getError()).isEqualTo("Workspace path may not contain ':': foo:bar");
  }
}
