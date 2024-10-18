package com.google.idea.blaze.base.buildview.events

import com.google.devtools.build.lib.buildeventstream.BuildEventStreamProtos.BuildEvent
import com.google.devtools.build.lib.buildeventstream.BuildEventStreamProtos.BuildEventId
import com.google.idea.blaze.base.scope.output.IssueOutput
import com.intellij.build.events.MessageEvent

private fun <T> reportMissingImplementation(id: BuildEventId): T? {
  LOG.error("missing implementation: $id")
  return null
}

class AbortedParser : BuildEventParser {
  private fun getDescription(id: BuildEventId): String? {
    return when {
      id.hasUnconfiguredLabel() -> "could not find label: ${id.unconfiguredLabel.label} - make sure a label or a file with that name exists"
      id.hasTargetConfigured() -> "could not configure target: ${id.targetConfigured.label}"
      id.hasTargetCompleted() -> "could not complete target: ${id.targetCompleted.label}"
      id.hasConfiguredLabel() -> null
      else -> reportMissingImplementation(id)
    }
  }

  private fun getChildDescription(id: BuildEventId): String? {
    return when {
      id.hasUnconfiguredLabel() -> "depends on undefined label: ${id.unconfiguredLabel.label} - might not be a direct dependency"
      id.hasConfiguredLabel() -> null
      else -> reportMissingImplementation(id)
    }
  }

  private fun buildDescription(event: BuildEvent): String? {
    val builder = StringBuilder()

    if (event.aborted.description.isNotBlank()) {
      builder.append(event.aborted.description)
    } else {
      builder.append(getDescription(event.id) ?: return null)
    }
    builder.append("\n\n")

    for (child in event.childrenList) {
      val description = getChildDescription(child) ?: return null

      builder.append(description)
      builder.append("\n")
    }

    return builder.toString().trimEnd()
  }

  private fun getLabel(id: BuildEventId): String? {
    return when {
      id.hasTargetConfigured() -> id.targetConfigured.label
      id.hasTargetCompleted() -> id.targetCompleted.label
      id.hasUnconfiguredLabel() -> id.unconfiguredLabel.label
      id.hasConfiguredLabel() -> id.configuredLabel.label
      id.hasUnstructuredCommandLine() -> null
      else -> reportMissingImplementation(id)
    }
  }

  override fun parse(event: BuildEvent): IssueOutput? {
    if (!event.hasAborted()) return null

    val label = getLabel(event.id) ?: return null
    val issue = BazelBuildIssue(
      label = label,
      title = "${event.aborted.reason}: $label",
      description = buildDescription(event) ?: return null,
    )

    return IssueOutput(issue, MessageEvent.Kind.ERROR)
  }
}