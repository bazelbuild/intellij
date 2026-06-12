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
package com.google.idea.blaze.base.run.state

import com.google.idea.blaze.base.command.BlazeFlags
import com.google.idea.blaze.base.execution.BlazeParametersListUtil
import com.google.idea.blaze.base.ui.UiUtil
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBTextField
import org.jdom.Element
import javax.swing.JComponent
import javax.swing.JLabel

private const val TEST_FILTER_XML_TAG = "blaze-test-filter"

/** State holding the Bazel `--test_filter` value for a test run configuration.  */
class TestFilterState : RunConfigurationState {

  /** The raw `--test_filter` value, or null when not set. */
  var testFilter: String? = null
    set (value) {
      field = if (value.isNullOrEmpty()) null else value
    }

  /** The rendered `--test_filter=<encoded>` flag, or null when not set.  */
  val testFilterFlag: String?
    get() {
      if (testFilter == null) {
        return null
      }

      return BlazeParametersListUtil.encodeTestFilterFlag(testFilter)
    }

  override fun readContext(context: DataContext?) {}

  override fun readExternal(element: Element) {
    element.getChild(TEST_FILTER_XML_TAG)?.let { testFilter = it.textTrim }
  }

  override fun writeExternal(element: Element) {
    element.removeChildren(TEST_FILTER_XML_TAG)
    testFilter?.let { element.addContent(Element(TEST_FILTER_XML_TAG).setText(it)) }
  }

  override fun getEditor(project: Project): RunConfigurationStateEditor {
    return TestFilterStateEditor()
  }

  /** Editor for [TestFilterState] — a single-line text field with a label. */
  internal class TestFilterStateEditor : RunConfigurationStateEditor {

    private val field = JBTextField()
    private val component = UiUtil.createBox(JLabel("Bazel test filter:"), field)

    override fun resetEditorFrom(genericState: RunConfigurationState) {
      val state = genericState as TestFilterState
      field.text = state.testFilter.orEmpty()
    }

    override fun applyEditorTo(genericState: RunConfigurationState?) {
      val state = genericState as TestFilterState
      state.testFilter =  field.text.trim()
    }

    override fun createComponent(): JComponent {
      return component
    }

    override fun setComponentEnabled(enabled: Boolean) {
      field.isEnabled = enabled
    }

    fun setComponentVisible(visible: Boolean) {
      component.isVisible = visible
    }
  }
}