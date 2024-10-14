package com.google.idea.blaze.base.buildview.events

import com.google.devtools.build.lib.buildeventstream.BuildEventStreamProtos.BuildEvent
import com.google.idea.blaze.base.scope.output.IssueOutput
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionPointName

internal val LOG = logger<BuildEventParser>()

interface BuildEventParser {
  companion object {
    val EP_NAME: ExtensionPointName<BuildEventParser> =
      ExtensionPointName.create("com.google.idea.blaze.BuildEventParser")

    fun parse(event: BuildEvent): IssueOutput? {
      return EP_NAME.extensions.firstNotNullOfOrNull { it.parse(event) }
    }
  }

  fun parse(event: BuildEvent): IssueOutput?
}
