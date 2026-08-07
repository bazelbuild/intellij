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
package com.google.idea.blaze.clwb

import com.google.common.truth.Truth.assertWithMessage
import com.google.idea.blaze.clwb.base.ClwbIntegrationTestCase
import com.google.idea.blaze.clwb.run.producers.NonBlazeProducerSuppressor
import com.intellij.execution.actions.RunConfigurationProducer
import com.jetbrains.cidr.execution.testing.CidrTestRunConfigurationBaseProducer
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * [com.intellij.execution.RunConfigurationProducerService.isIgnored] matches producers by their exact
 * class name, so a producer moved to a different package silently comes back to life. This makes sure
 * the suppression list keeps up with the producers the IDE actually registers.
 */
@RunWith(JUnit4::class)
class NonBlazeProducerSuppressorTest : ClwbIntegrationTestCase() {

  @Test
  fun `all cidr test configuration producers are suppressed`() {
    val producers = RunConfigurationProducer.EP_NAME.extensionList
      .filterIsInstance<CidrTestRunConfigurationBaseProducer<*, *, *, *>>()
      .map { it.javaClass.name }

    assertWithMessage("no CIDR test configuration producer is registered at all")
      .that(producers).isNotEmpty()

    for (producer in producers) {
      assertWithMessage("$producer is not suppressed in blaze projects")
        .that(NonBlazeProducerSuppressor.PRODUCERS_TO_SUPPRESS).contains(producer)
    }
  }
}