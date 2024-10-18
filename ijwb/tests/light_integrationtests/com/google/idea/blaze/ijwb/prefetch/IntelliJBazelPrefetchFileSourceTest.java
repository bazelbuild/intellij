/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.ijwb.prefetch;

import static com.google.common.truth.Truth.assertThat;

import com.google.idea.blaze.base.BlazeIntegrationTestCase;
import com.google.idea.blaze.base.prefetch.PrefetchFileSource;
import com.intellij.util.PlatformUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Test for file extensions prefetched in the IntelliJ Bazel plugin. */
@RunWith(JUnit4.class)
public class IntelliJBazelPrefetchFileSourceTest extends BlazeIntegrationTestCase {

  @Test
  public void testPrefetchedExtensions() {
    if (PlatformUtils.isIdeaUltimate()) {
      assertThat(PrefetchFileSource.getAllPrefetchFileExtensions())
          .containsExactly("java", "proto", "dart", "js", "html", "css", "gss");
    } else {
      assertThat(PrefetchFileSource.getAllPrefetchFileExtensions())
          .containsExactly("java", "proto", "dart");
    }
  }
}
