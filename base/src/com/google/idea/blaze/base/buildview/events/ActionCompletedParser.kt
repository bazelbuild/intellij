package com.google.idea.blaze.base.buildview.events

import com.google.devtools.build.lib.buildeventstream.BuildEventStreamProtos.*
import com.google.idea.blaze.base.scope.output.IssueOutput
import com.intellij.build.events.MessageEvent
import java.io.IOException
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path

class ActionCompletedParser : BuildEventParser {
  private fun getDescription(body: ActionExecuted): String? {
    if (!body.hasStderr()) {
      if (body.hasFailureDetail()) {
        return body.failureDetail.message
      }

      return null
    }

    val uri = try {
      URI.create(body.stderr.uri)
    } catch (e: IllegalArgumentException) {
      return "Invalid output URI: ${body.stderr.uri}"
    }

    return try {
      Files.readString(Path.of(uri))
    } catch (e: IOException) {
      "Could not read output file: ${e.message}"
    }
  }

  override fun parse(event: BuildEvent): IssueOutput? {
    if (!event.id.hasActionCompleted()) return null
    val id = event.id.actionCompleted

    val isExternal = !id.label.startsWith("//")

    if (!event.hasAction()) return null
    val body = event.action

    val isWarning = !body.hasFailureDetail()

    // ignore warnings from external projects
    if (isExternal && isWarning) return null

    val name = if (isWarning) {
      "BUILD_WARNING"
    } else {
      "BUILD_FAILURE"
    }

    val issue = BazelBuildIssue(
      label = id.label,
      title = "$name: ${id.label}",
      description = getDescription(body) ?: return null,
    )

    // TODO: if this is reused for a build view, this logic needs to be adjusted
    return IssueOutput(
      issue,
      if (isWarning) MessageEvent.Kind.INFO else MessageEvent.Kind.WARNING,
    )
  }
}
