/*
 * Copyright 2026 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.idea.blaze.clwb

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.google.idea.blaze.base.lang.buildfile.psi.FuncallExpression
import com.google.idea.blaze.base.lang.buildfile.psi.ReferenceExpression
import com.google.idea.blaze.base.model.primitives.Label
import com.google.idea.blaze.base.run.producers.BuildFileRunLineMarkerContributor
import com.google.idea.blaze.clwb.base.ClwbHeadlessTestCase
import com.intellij.icons.AllIcons
import com.intellij.openapi.application.runReadAction
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * Verifies that the run/test gutter icons are *present* for BUILD-file targets after a sync, without
 * executing any of them (a lighter counterpart to [ExecutionTest]).
 *
 * The C++ source gutter icons are intentionally not covered here: under the Radler engine used by
 * these tests, per-test/run markers come from the (.NET) Radler backend, which the headless harness
 * does not bring up. BUILD-file markers, in contrast, are produced by
 * [BuildFileRunLineMarkerContributor] and are engine-independent.
 */
@RunWith(JUnit4::class)
class GutterTest : ClwbHeadlessTestCase() {

  private val markerContributor = BuildFileRunLineMarkerContributor()

  @Test
  fun testClwb() {
    val errors = runSync(defaultSyncParams().build())
    errors.assertNoErrors()

    assertGutterPresent(Label.create("//main:echo0")) // cc_binary
    assertGutterPresent(Label.create("//main:gtest")) // cc_test
    assertGutterPresent(Label.create("//main:catch")) // cc_test
  }

  /** Asserts that a run/test gutter icon is contributed for the rule [label] in its BUILD file. */
  private fun assertGutterPresent(label: Label) {
    val funcall = findRule(label)

    val info = runReadAction { markerContributor.getInfo(ruleNameLeaf(funcall)) }

    assertWithMessage("no gutter run marker for $label").that(info).isNotNull()
    assertThat(info!!.icon).isEqualTo(AllIcons.RunConfigurations.TestState.Run)
  }

  /** The leaf holding the rule's function name, where [BuildFileRunLineMarkerContributor] anchors. */
  private fun ruleNameLeaf(funcall: FuncallExpression): LeafPsiElement {
    val leaf = PsiTreeUtil.findChildrenOfType(funcall, LeafPsiElement::class.java)
      .firstOrNull { it.parent is ReferenceExpression && it.parent.parent === funcall }
    return requireNotNull(leaf) { "rule name leaf not found in $funcall" }
  }
}
